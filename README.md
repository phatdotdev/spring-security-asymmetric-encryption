# Spring Security JWT bất đối xứng — hướng dẫn xây dựng và đọc hiểu dự án

Tài liệu này giải thích mã nguồn trong [asymmectric-encryption](asymmectric-encryption), dựa trên khung 17 bước của [README gốc](asymmectric-encryption/README.md). README gốc được giữ nguyên. Các phần dưới mô tả **code đang có**, sau đó mới đưa ví dụ mở rộng hoặc hướng cải thiện; ví dụ mở rộng chưa được áp dụng vào mã nguồn.

Dự án là REST API quản lý tài khoản: đăng ký, đăng nhập, cấp JWT, làm mới access token, xem/sửa hồ sơ, đổi mật khẩu và bật/tắt tài khoản. Mật khẩu được băm bằng BCrypt. JWT được ký bằng RSA private key và kiểm tra bằng public key. Dù tên thư mục là `asymmectric-encryption`, luồng JWT ở đây thực hiện **ký số**, không mã hóa nội dung token.

Tài liệu được đối chiếu với mã nguồn ngày **21/11/2026**. Các giới hạn còn thấy trong code được trình bày ngay tại phần liên quan và tổng hợp ở cuối. Không nên đọc các luồng thành công dưới đây như bằng chứng mọi tình huống lỗi đã được xử lý đầy đủ.

## Cách đọc và mục lục

Nếu mới học, đọc phần bản đồ trước, rồi đi từ bước 1 đến bước 17. Khi gặp `extends`, `implements`, lambda, generic hoặc proxy, tra phần 18. Phần 19–20 là kiến thức mở rộng có ví dụ.

| Phần | Nội dung |
|---|---|
| [Bản đồ](#ban-do) | Cấu trúc, quan hệ giữa các lớp, ba luồng chính |
| [1](#buoc-1) | Maven, dependency, Spring Boot và bean |
| [2](#buoc-2) | Cấu hình ứng dụng và database |
| [3](#buoc-3) | SecurityFilterChain, CSRF, session, URL pattern |
| [4](#buoc-4) | User, Role, BaseEntity và quan hệ nhiều–nhiều |
| [5](#buoc-5) | UserService, repository, mapper và BCrypt |
| [6](#buoc-6) | UserController, principal và HTTP response |
| [7](#buoc-7) | Public/private key, PEM, JWT, access/refresh token |
| [8](#buoc-8) | JwtFilter và SecurityContextHolder |
| [9](#buoc-9) | DTO xác thực và tên trường JSON |
| [10](#buoc-10) | Đăng ký, đăng nhập, refresh và transaction |
| [11](#buoc-11) | AuthController và các endpoint công khai |
| [12](#buoc-12) | Auditing, listener, tự gán lúc tạo và chỉnh sửa |
| [13](#buoc-13) | Validation và giải thích từng regex |
| [14](#buoc-14) | Annotation tự định nghĩa và validator email |
| [15](#buoc-15) | Exception, mã lỗi, MVC handler và lỗi filter |
| [16](#buoc-16) | OpenAPI, Swagger và annotation tài liệu |
| [17](#buoc-17) | Chạy dự án, thử API và kiểm chứng |
| [18](#java) | Java: kế thừa, interface, impl, lambda, generic, pattern |
| [19](#jpa) | JPA: lifecycle, transaction, 1–1, 1–n, n–1 |
| [20](#phan-quyen) | Ví dụ phân quyền role, authority và chủ sở hữu |
| [21](#doi-chieu) | Những chỗ README cũ đã lỗi thời và giới hạn hiện tại |
| [22](#tra-cuu) | Danh mục toàn bộ lớp và annotation để tra cứu |
| [23](#tai-lieu) | Tài liệu chính thức dùng để đối chiếu khái niệm |

<a id="ban-do"></a>
## Bản đồ dự án và luồng tổng thể

Đường dẫn Java bên dưới đều tương đối với `asymmectric-encryption/src/main/java/com/doph/`.

```text
spring-security/
├── README.md                         ← tài liệu mới này
└── asymmectric-encryption/
    ├── README.md                     ← tài liệu gốc
    ├── pom.xml
    ├── mvnw, mvnw.cmd, .mvn/          ← Maven Wrapper
    ├── docker-compose.yml            ← PostgreSQL local
    ├── .env.example                  ← mẫu biến môi trường
    └── src/
        ├── main/
        │   ├── java/com/doph/
        │   │   ├── AsymmectricEncryptionApplication.java
        │   │   ├── auth/             ← API và nghiệp vụ xác thực
        │   │   ├── security/         ← filter, JWT, RSA, policy
        │   │   ├── user/             ← tài khoản, DTO, repository
        │   │   ├── role/             ← vai trò và repository
        │   │   ├── common/           ← BaseEntity
        │   │   ├── config/           ← bean, auditing, OpenAPI
        │   │   ├── validation/       ← constraint email tự tạo
        │   │   ├── exception/        ← BusinessException, ErrorCode
        │   │   └── handler/          ← đổi exception thành HTTP JSON
        │   └── resources/
        │       ├── application.yml
        │       └── keys/local-only/  ← cặp khóa phục vụ demo
        └── test/java/com/doph/
            └── AsymmectricEncryptionApplicationTests.java
```

```mermaid
flowchart TD
    C[Client: Postman / frontend] --> F[Spring Security FilterChain]
    F --> J[JwtFilter: nếu có Bearer token]
    J --> S[JwtService: kiểm tra chữ ký và claims]
    J --> UDS[UserServiceImpl.loadUserByUsername]
    UDS --> UR[UserRepository]
    UR --> DB[(PostgreSQL)]
    J --> CTX[SecurityContext chứa Authentication]
    CTX --> A[Kiểm tra quyền truy cập URL]
    A --> MVC[DispatcherServlet / Controller]
    MVC --> SV[Service xử lý nghiệp vụ]
    SV --> RP[Repository / JPA / Hibernate]
    RP --> DB
    SV --> DTO[Mapper / Response DTO]
    DTO --> C
    MVC -. lỗi trong MVC .-> EH[ApplicationExceptionHandler]
    EH --> C
```

Sơ đồ lược bỏ các filter mặc định khác. Request `/api/v1/auth...` được `JwtFilter` bỏ qua; việc bỏ qua filter không tự cấp quyền cho mọi URL có tiền tố này. `SecurityConfig` quyết định URL nào được `permitAll()`.

Ba luồng cần nắm:

1. **Đăng ký:** JSON → validation → kiểm tra trùng → lấy `ROLE_USER` → băm mật khẩu → lưu `User` và liên kết role → `201`.
2. **Đăng nhập:** email/password → `AuthenticationManager` → `UserDetailsService` → database + BCrypt → lấy `User` đã xác thực → ký hai token → `200`.
3. **Gọi API `/users/me`:** Bearer JWT → filter xác minh → đọc user/quyền từ database → đặt `Authentication` vào context → controller lấy principal → trả DTO.

<a id="buoc-1"></a>
## 1. Tạo và cấu hình dự án — `pom.xml`, Spring Boot, bean

### 1.1. Công nghệ khai báo trong mã nguồn

| Thành phần | Khai báo / vai trò |
|---|---|
| Java | `java.version=25` trong POM |
| Spring Boot | Parent `4.1.2`; quản lý nhiều version dependency/plugin |
| Spring Web MVC | `spring-boot-starter-webmvc`: nhận HTTP, ánh xạ controller, JSON |
| Spring Security | Filter chain, Authentication, BCrypt, authorization |
| Spring Data JPA | Repository, tích hợp JPA/Hibernate, auditing |
| Jakarta Validation | Ràng buộc request qua `@Valid`, `@NotBlank`… |
| PostgreSQL | JDBC driver ở scope `runtime`; database trong Compose là `postgres:16-alpine` |
| JJWT | `0.12.6`: API tạo/đọc JWT, implementation, Jackson integration |
| springdoc OpenAPI | `3.2.0`: sinh OpenAPI và Swagger UI |
| Lombok | Sinh getter, setter, constructor, builder, logger khi biên dịch |
| Test starters | Hỗ trợ kiểm thử JPA, Security, Validation, MVC |

Đây là **version được dự án khai báo**, không phải kết luận về version mới nhất hay xác nhận build đã thành công. Dependency không chỉ có những gì viết trực tiếp trong POM: Maven còn kéo dependency bắc cầu. Khi dùng `StringUtils` của Apache Commons Lang trong `UserMapper`, cần kiểm tra dependency tree nếu build báo thiếu thư viện.

`jjwt-api` cung cấp các kiểu như `Jwts`, `Claims`; `jjwt-impl` cung cấp thực thi; `jjwt-jackson` xử lý JSON. Có API mà thiếu implementation lúc chạy vẫn có thể lỗi. `<scope>test</scope>` chỉ dành cho test; `<scope>runtime</scope>` cung cấp khi chạy. Lombok được khai báo là annotation processor cho compile và test-compile: annotation của Lombok tạo mã Java, không tự chạy nghiệp vụ vào mỗi request.

### 1.2. Điểm vào chương trình

File: [AsymmectricEncryptionApplication.java](asymmectric-encryption/src/main/java/com/doph/AsymmectricEncryptionApplication.java).

```java
@SpringBootApplication
public class AsymmectricEncryptionApplication {
    public static void main(String[] args) {
        SpringApplication.run(AsymmectricEncryptionApplication.class, args);
    }
}
```

`main` là điểm vào Java. `SpringApplication.run` dựng application context và khởi động ứng dụng. `@SpringBootApplication` gộp cấu hình Spring Boot, bật auto-configuration và component scanning. Vì lớp này ở package `com.doph`, Spring quét các package con như `com.doph.user`, `com.doph.security`.

**Bean** là đối tượng được Spring tạo/quản lý. `@Service`, `@Component`, `@RestController`, `@Configuration` giúp phát hiện các lớp phù hợp; `@Bean` đăng ký đối tượng trả về từ một phương thức cấu hình. Entity `User` không vì có `@Entity` mà trở thành singleton service của Spring: đối tượng entity do ứng dụng/JPA quản lý theo vòng đời dữ liệu.

Ví dụ `UserServiceImpl` có ba field `final`. `@RequiredArgsConstructor` tạo constructor nhận chúng. Spring tìm bean tương ứng rồi truyền vào. Đây là **constructor injection**; bạn không phải `new UserRepository()`.

### 1.3. `CommandLineRunner` — chạy logic sau khi khởi tạo

Trong lớp application có bean:

```java
@Bean
public CommandLineRunner commandLineRunner(final RoleRepository roleRepository) {
    return args -> {
        final Optional<Role> userRole = roleRepository.findByName("USER_ROLE");
        if (userRole.isEmpty()) {
            final Role role = new Role();
            role.setName("ROLE_USER");
            roleRepository.save(role);
        }
    };
}
```

Đây là đoạn trích giữ nguyên logic hiện tại, bỏ một dấu `;` thừa không ảnh hưởng hành vi. Spring truyền repository vào phương thức `@Bean`. Giá trị trả về là lambda thực thi `CommandLineRunner.run(String... args)`. Boot gọi runner sau khi context được khởi tạo, trước khi hoàn tất quy trình startup. Runner chạy lại **mỗi lần khởi động**, không phải mỗi HTTP request. Dùng nó để seed dữ liệu ban đầu hoặc kiểm tra điều kiện khởi động; nó không phải scheduled job. Xem [tài liệu SpringApplication](https://docs.spring.io/spring-boot/reference/features/spring-application.html#features.spring-application.command-line-runner).

**Lệch trong code:** tìm `USER_ROLE` nhưng tạo `ROLE_USER`; đăng ký cũng tìm `ROLE_USER`. Vì vậy điều kiện “đã có role chưa” không đúng, có thể sinh nhiều dòng `ROLE_USER` sau nhiều lần chạy. `Role.name` hiện chưa khai báo `unique=true`. Hướng sửa là dùng cùng một tên khi tìm/tạo và bảo vệ tính duy nhất ở database; chỉ đổi tên trong README không sửa được dữ liệu trùng đã tồn tại.

<a id="buoc-2"></a>
## 2. Thêm cấu hình — `application.yml`, `.env`, PostgreSQL

File: [application.yml](asymmectric-encryption/src/main/resources/application.yml), [docker-compose.yml](asymmectric-encryption/docker-compose.yml), [.env.example](asymmectric-encryption/.env.example).

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
  datasource:
    url: jdbc:postgresql://${DB_URL}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update

app:
  security:
    jwt:
      access-token-expiration: 86400000
      refresh-token-expiration: 604800000
```

Đây là phần trích, không thay thế toàn bộ file. `${DB_URL}` là placeholder lấy từ môi trường cấu hình của Spring. `optional:file:.env[.properties]` cho phép đọc file `.env` theo cú pháp Java properties; `optional` cho phép file vắng mặt, nhưng các placeholder bắt buộc vẫn phải có giá trị từ nguồn khác. Đường dẫn `.env` phụ thuộc thư mục chạy tiến trình, vì thế các lệnh chạy ở phần 17 đều chuyển vào thư mục module.

| Thuộc tính | Ý nghĩa thực tế |
|---|---|
| `spring.application.name` | Tên ứng dụng; không tự tạo database |
| `datasource.url` | Địa chỉ JDBC, gồm host, port, tên database |
| `driver-class-name` | Chọn driver PostgreSQL |
| `ddl-auto: update` | Hibernate cố cập nhật schema theo mapping; không phải công cụ quản lý migration đầy đủ |
| `database` / `database-platform` | Chỉ định database/dialect PostgreSQL |
| `show-sql: false` | Không bật in SQL theo tùy chọn này |
| `hibernate.format_sql: true` | Định dạng SQL khi SQL được log; không tự bật log SQL |
| `access-token-expiration` | Code cộng vào milliseconds hiện tại: `86400000` = 24 giờ |
| `refresh-token-expiration` | `604800000` milliseconds = 7 ngày |
| `disposable-email-domains` | Danh sách domain bị cấm, inject vào validator |

Compose mở PostgreSQL tại `localhost:5432`, tạo database `spring_app_db` với tài khoản demo `username/password`, lưu dữ liệu trong named volume `postgres_data`. `.env.example` lại để `DB_NAME=dbname`: phải sửa giá trị trong `.env` cho khớp database đã tạo. Nếu ứng dụng chạy trong container khác, host database thường dùng tên service `postgres`; `localhost` trong container chỉ chính container đó.

<a id="buoc-3"></a>
## 3. Cấu hình bảo mật — filter, CSRF, session và matcher

File: [SecurityConfig.java](asymmectric-encryption/src/main/java/com/doph/security/SecurityConfig.java).

### 3.1. Giải nghĩa annotation và từng mắt xích

| Thành phần | Vai trò |
|---|---|
| `@Configuration` | Lớp khai báo cấu hình/bean |
| `@EnableWebSecurity` | Bật hạ tầng bảo mật web và tích hợp chain cấu hình |
| `@EnableMethodSecurity` | Cho phép kiểm tra quyền trên method; hiện chưa có `@PreAuthorize` trong dự án |
| `@RequiredArgsConstructor` | Sinh constructor inject `JwtFilter` |
| `@Bean filterChain(...)` | Đăng ký `SecurityFilterChain` do `HttpSecurity` xây dựng |
| `HttpSecurity` | Builder cấu hình bảo mật request |
| `.build()` | Hoàn tất builder, trả đối tượng chain |

```java
return http
    .csrf(AbstractHttpConfigurer::disable)
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(PUBLIC_URLS).permitAll()
        .anyRequest().authenticated())
    .sessionManagement(sess ->
        sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .addFilterBefore((Filter) this.jwtFilter,
                    UsernamePasswordAuthenticationFilter.class)
    .build();
```

Các lời gọi nối nhau tạo cấu hình, không chạy lại toàn bộ builder cho từng request. Khi request tới, filter chain được dựng sẵn mới xử lý nó. `AbstractHttpConfigurer::disable` là method reference; có thể đọc tương đương `configurer -> configurer.disable()` trong ngữ cảnh này.

`requestMatchers(PUBLIC_URLS).permitAll()` mở các đường dẫn auth được liệt kê, Swagger và trang gốc. `.anyRequest().authenticated()` yêu cầu mọi request còn lại có authentication được chấp nhận. Đây mới là kiểm tra **đã xác thực**, chưa yêu cầu `ROLE_ADMIN` hay quyền cụ thể. Việc cho phép `/` không tự tạo controller hay trang HTML tại `/`.

### 3.2. Pattern URL khác regex Java

Trong danh sách public có đường dẫn chính xác `/api/v1/auth/login` và mẫu `/swagger-ui/**`. `/**` diễn đạt các đường dẫn con theo cơ chế matcher đường dẫn của Spring; nó không phải regex `java.util.regex.Pattern`. Ví dụ `/swagger-ui/index.html` thuộc nhánh Swagger. Thứ tự rule quan trọng: đặt rule cụ thể trước rule tổng quát như `anyRequest()`.

Trong khi đó `request.getServletPath().startsWith("/api/v1/auth")` ở filter chỉ là phép so sánh tiền tố chuỗi. Nó còn khớp `/api/v1/authentication`; nhưng URL này vẫn phải qua quy tắc authorization của chain. Không được đồng nhất việc filter bỏ qua với `permitAll()`.

### 3.3. Filter hoạt động thế nào?

Servlet filter có thể đọc request, tạo response từ chối, thêm thông tin, hoặc gọi `filterChain.doFilter(request, response)` để chuyển tiếp. Code phía sau lời gọi có thể chạy khi các thành phần bên trong trả về, giống một chuỗi bao quanh nhau.

Spring Security có nhiều filter phục vụ context, exploit protection, authentication và authorization. `JwtFilter` là filter tự viết. `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)` dùng lớp filter chuẩn làm mốc thứ tự; nó không biến `JwtFilter` thành filter đăng nhập bằng form và không chứng minh form login đã được bật. Login của dự án là controller JSON tự gọi manager. Xem [kiến trúc filter của Spring Security](https://docs.spring.io/spring-security/reference/servlet/architecture.html).

Ép kiểu `(Filter)` hiện có trong code là dư về mặt quan hệ kiểu: `JwtFilter` kế thừa `OncePerRequestFilter`, vốn đã là servlet `Filter`.

### 3.4. Session và `STATELESS`

Với session authentication thường gặp, server lưu trạng thái đăng nhập trong HTTP session và browser gửi cookie session ID. Với cấu hình này, Spring Security dùng `STATELESS`: không tạo/dùng HTTP session để lưu và lấy `SecurityContext` cho việc xác thực. Mỗi request được bảo vệ phải mang token để dựng lại authentication.

`STATELESS` vẫn có `SecurityContext` trong thời gian xử lý request. Nó cũng không cấm toàn bộ ứng dụng gọi API tạo HTTP session, không xóa database và không có nghĩa server không đọc database. Chính `JwtFilter` ở đây vẫn tải user từ repository mỗi request có token. Context mặc định gắn với thread và được hạ tầng Security dọn sau xử lý; không tự suy ra nó sẽ truyền sang mọi thread nền. Xem [session management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html).

### 3.5. CSRF: tại sao tắt, khi nào phải xem lại?

CSRF xảy ra khi trang tấn công khiến browser gửi một request với thông tin xác thực browser tự đính kèm, thường là cookie phiên. Server nhận cookie hợp lệ nhưng hành động không xuất phát từ ý định của người dùng. CSRF token giúp xác minh request thay đổi dữ liệu có nguồn hợp lệ.

Dự án tắt CSRF vì luồng dự kiến là client chủ động gửi `Authorization: Bearer ...`. Browser không tự thêm header Bearer của ứng dụng cho một form từ website khác. Nhưng **chỉ có JWT hoặc `STATELESS` chưa đủ để kết luận miễn nhiễm CSRF**: nếu đổi sang lưu JWT trong cookie tự gửi, phải đánh giá lại và cấu hình bảo vệ phù hợp. CORS là chính sách browser về truy cập liên nguồn, không thay thế CSRF; HTTPS bảo vệ đường truyền, XSS là kiểu tấn công khác. Xem [CSRF của Spring Security](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

Code hiện chưa cấu hình CORS riêng. Gọi từ Postman thành công không chứng minh frontend ở origin khác sẽ qua CORS.

<a id="buoc-4"></a>
## 4. Xây entity — `User`, `BaseEntity`, `Role`

File: [User.java](asymmectric-encryption/src/main/java/com/doph/user/User.java), [BaseEntity.java](asymmectric-encryption/src/main/java/com/doph/common/BaseEntity.java), [Role.java](asymmectric-encryption/src/main/java/com/doph/role/Role.java).

### 4.1. Entity, bảng và khóa chính

`User` dùng `@Entity` để được JPA quản lý và `@Table(name = "USERS")` để chỉ định bảng logic. `@Id` đánh dấu khóa chính; `@GeneratedValue(strategy = GenerationType.UUID)` yêu cầu provider sinh định danh UUID, dù kiểu field của dự án là `String`. UUID không phải token và không dùng thay cơ chế kiểm tra quyền.

Field định danh của `User` đang viết `private String Id;`, khác quy ước camelCase `id`. Lombok vẫn tạo `getId()` được code sử dụng. Annotation JPA đặt trên field khiến mapping dùng field access; việc tồn tại getter `getUsername()` của Security không tự tạo thêm cột username. Tên bảng/cột SQL thực tế còn phụ thuộc naming strategy và quy tắc quoted/unquoted identifier của database.

`@Column(name=..., nullable=false, unique=true)` khai báo mapping và ràng buộc schema. `nullable=false` không phải thông báo validation thân thiện cho JSON; nếu request lọt đến database với dữ liệu không hợp lệ, nó có thể thành exception persistence. `unique=true` ở email là unique thông thường, không tự tạo unique không phân biệt hoa thường. `existsByEmailIgnoreCase` kiểm tra trước để báo dễ hiểu nhưng chưa loại bỏ được race condition giữa hai request đồng thời.

### 4.2. Các nhóm field của `User`

| Field | Ý nghĩa / nơi dùng |
|---|---|
| `firstName`, `lastName` | Thông tin hồ sơ; mapper cập nhật nếu có giá trị khác và không trắng |
| `email` | Unique theo mapping, dùng làm username khi login và làm JWT subject |
| `phoneNumber` | Unique theo mapping, được kiểm tra trùng lúc đăng ký |
| `password` | Hash BCrypt; không trả trong `UserResponse` |
| `dateOfBirth` | `LocalDate`, có trong entity/DTO; mapper cập nhật hiện chưa xử lý |
| `enabled` | Tài khoản có được cho phép đăng nhập hay không |
| `locked` | Có bị khóa không; `isAccountNonLocked()` trả `!locked` |
| `credentialsExpired` | Mật khẩu/credentials có hết hạn không; getter Security đảo giá trị |
| `emailVerified`, `phoneVerified` | Cờ trạng thái; chưa có luồng xác minh trong dự án |
| `createdDate`, `lastModifiedDate` | Thời điểm tạo/sửa qua auditing listener |
| `roles` | Danh sách vai trò chuyển thành Security authorities |

`LocalDate` chỉ có ngày, còn `LocalDateTime` có ngày giờ nhưng không mang timezone/offset. Các cờ `emailVerified=false` và `phoneVerified=false` không tự ngăn đăng nhập: code chưa dùng chúng làm điều kiện xác thực.

### 4.3. `User implements UserDetails` để làm gì?

`UserDetails` là hợp đồng dữ liệu mà Spring Security hiểu. `User` vừa là entity lưu database, vừa cung cấp dữ liệu xác thực theo hợp đồng đó.

| Method | Hành vi hiện tại |
|---|---|
| `getUsername()` | Trả email, không trả ID |
| `getPassword()` | Trả `this.password`, tức hash; không gọi lại chính getter |
| `getAuthorities()` | Mỗi `Role.name` thành một `SimpleGrantedAuthority` |
| `isAccountNonExpired()` | Luôn `true`: chưa triển khai ngày hết hạn tài khoản |
| `isAccountNonLocked()` | `!locked` |
| `isCredentialsNonExpired()` | `!credentialsExpired` |
| `isEnabled()` | Getter boolean do Lombok sinh từ `enabled` |

Đoạn cốt lõi:

```java
return this.roles.stream()
    .map(role -> new SimpleGrantedAuthority(role.getName()))
    .toList();
```

Nếu role có tên `ROLE_USER`, authority nhận đúng chuỗi `ROLE_USER`; không có phép đổi tên hay tự thêm `ROLE_` tại đây. Khi roles null/rỗng, `CollectionUtils.isEmpty` giúp trả danh sách rỗng. User không có authority vẫn có thể qua rule chỉ yêu cầu `authenticated()` nếu authentication đã được chấp nhận.

README cũ từng có `return this.getPassword();`: đó là đệ quy vô hạn gây `StackOverflowError`. Mã nguồn hiện đã dùng `return this.password;` đúng. Các method trạng thái được provider dùng lúc login; filter JWT tự dựng authentication hiện chưa kiểm tra các trạng thái đó, xem bước 8.

### 4.4. `BaseEntity`: tái sử dụng mapping bằng kế thừa

`@MappedSuperclass` cho phép entity con kế thừa field mapping như `id`, `createdDate`, `createdBy`. JPA không tạo một bảng `BaseEntity` độc lập chỉ vì annotation này; đây cũng không phải một entity để truy vấn trực tiếp như `User`.

Trong code:

```text
BaseEntity
└── Role extends BaseEntity

User implements UserDetails       ← không extends BaseEntity
```

Vì vậy **Role có cả audit thời gian và người thao tác**, còn **User chỉ có hai field thời gian tự khai báo**. Không được nói `User.createdBy` đang được tự gán, vì `User` hiện không có field đó.

`@SuperBuilder` phục vụ builder có kế thừa; cả `BaseEntity` và `Role` dùng annotation này. `@Builder` thường trên `User` không tự cung cấp cơ chế builder field cha tương đương. Package của `SuperBuilder` có thay đổi theo version Lombok, nên khi gặp lỗi import phải đối chiếu đúng dependency được resolve, thay vì sao chép import của một phiên bản khác. Xem [Lombok SuperBuilder](https://projectlombok.org/features/SuperBuilder).

### 4.5. Quan hệ nhiều–nhiều `User` ↔ `Role`

Một user có thể có nhiều role; một role có thể thuộc nhiều user. Bảng nối lưu từng cặp liên kết:

```mermaid
erDiagram
    USERS ||--o{ USERS_ROLES : has
    ROLES ||--o{ USERS_ROLES : assigned
    USERS {
        string id PK
        string email
    }
    ROLES {
        string id PK
        string name
    }
    USERS_ROLES {
        string USERS_ID FK
        string ROLES_ID FK
    }
```

Tên trong sơ đồ là cách mô tả logic. Annotation của `User`:

```java
@ManyToMany(
    cascade = {CascadeType.PERSIST, CascadeType.MERGE},
    fetch = FetchType.EAGER
)
@JoinTable(
    name = "USERS_ROLES",
    joinColumns = @JoinColumn(name = "USERS_ID"),
    inverseJoinColumns = @JoinColumn(name = "ROLES_ID")
)
private List<Role> roles;
```

`User.roles` là **owning side**, nơi mô tả bảng nối. `joinColumns` trỏ về user hiện tại; `inverseJoinColumns` trỏ về role. Trong `Role`:

```java
@ManyToMany(mappedBy = "roles")
private List<User> users;
```

`mappedBy="roles"` nói liên kết được quản lý bởi **field Java `roles` ở `User`**, không phải bởi tên bảng `ROLES`. `Role.users` là inverse side. Cập nhật bên sở hữu mới quyết định thay đổi liên kết ở database; vẫn nên đồng bộ hai phía trong bộ nhớ để code không nhìn thấy dữ liệu lệch. Xem [quy tắc quan hệ JPA](https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-intro/persistence-intro.html).

`PERSIST` truyền thao tác persist sang entity liên quan; `MERGE` truyền merge. Cascade không có nghĩa “mỗi lần save user thì insert lại mọi role”, không tự là SQL `ON DELETE CASCADE`, và không phải fetch. Dự án không cấu hình `REMOVE` cho role; đây là điều phù hợp với role được chia sẻ bởi nhiều user.

`EAGER` yêu cầu roles được nạp sớm khi load user, thuận tiện khi tạo authorities. Nó không bảo đảm Hibernate chỉ chạy một câu `JOIN`; provider có thể dùng query bổ sung. `Role.users` dùng mặc định lazy của many-to-many. Trả entity hai chiều trực tiếp thành JSON dễ gây vòng lặp user → roles → users và kéo theo dữ liệu không cần thiết, nên dự án dùng `UserResponse`.

<a id="buoc-5"></a>
## 5. Xây service, repository, mapper và password encoder

### 5.1. `UserService` và `UserDetailsService` khác nhau thế nào?

File: [UserService.java](asymmectric-encryption/src/main/java/com/doph/user/UserService.java), [UserServiceImpl.java](asymmectric-encryption/src/main/java/com/doph/user/impl/UserServiceImpl.java).

```java
public interface UserService extends UserDetailsService {
    void updateProfileInformation(ProfileUpdateRequest request, String userId);
    void changePassword(ChangePasswordRequest request, String userId);
    void deactivateAccount(String userId);
    void reactivateAccount(String userId);
    void deleteAccount(String userId);
}
```

`UserService` mở rộng hợp đồng framework để bổ sung nghiệp vụ ứng dụng. `UserServiceImpl implements UserService` phải thực thi cả các method tự khai báo và `loadUserByUsername` được kế thừa từ `UserDetailsService`.

```java
@Override
public UserDetails loadUserByUsername(final String email)
        throws UsernameNotFoundException {
    return userRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new UsernameNotFoundException(
            "User not found with username: " + email));
}
```

Method này **tìm user theo định danh**, không nhận mật khẩu và không tự so sánh mật khẩu. Nó trả `User` dưới kiểu interface `UserDetails`. Login provider và `JwtFilter` đều có thể gọi cùng hợp đồng này. `UserDetails` là dữ liệu; `UserDetailsService` là dịch vụ tìm dữ liệu đó.

### 5.2. Repository: vì sao interface không có code SQL vẫn chạy?

File: [UserRepository.java](asymmectric-encryption/src/main/java/com/doph/user/UserRepository.java), [RoleRepository.java](asymmectric-encryption/src/main/java/com/doph/role/RoleRepository.java).

```java
public interface UserRepository extends JpaRepository<User, String> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}
```

`JpaRepository<User, String>` nghĩa là repository cho entity `User`, khóa chính kiểu `String`. Spring Data tạo proxy implementation lúc chạy. `findById`, `save`… có sẵn từ hệ interface repository; các method phía trên được phân tích theo quy tắc derived query: `existsBy` hỏi có tồn tại không, `findBy` tìm dữ liệu, `Email` là thuộc tính entity, `IgnoreCase` yêu cầu so sánh không phân biệt hoa thường.

Tên method không phải chuỗi tiếng Anh tùy ý. README cũ từng ghi `findByEmailIgnoreCAse`; mã nguồn hiện dùng `findByEmailIgnoreCase`. `RoleRepository.findByName` trả `Optional<Role>`; nếu dữ liệu trùng tên tạo nhiều kết quả, `Optional` không tự chọn một dòng để che lỗi.

### 5.3. `BeanConfig` và BCrypt

File: [BeanConfig.java](asymmectric-encryption/src/main/java/com/doph/config/BeanConfig.java).

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

Biến/field dùng interface `PasswordEncoder`, đối tượng cụ thể là `BCryptPasswordEncoder`. Lúc tạo tài khoản hoặc đổi mật khẩu, gọi `encode(rawPassword)`; lúc kiểm tra, gọi `matches(rawPassword, storedHash)`.

BCrypt là phép băm mật khẩu có salt, không có public/private key và không có thao tác giải mã để lấy lại mật khẩu gốc. Hai lần encode cùng mật khẩu có thể ra hash khác nhau, vì thế không kiểm tra bằng `encode(input).equals(storedHash)`. Dự án dùng `matches` đúng ở chức năng đổi mật khẩu; login provider dùng encoder tương tự.

### 5.4. `UserMapper`: ba hướng chuyển đổi

File: [UserMapper.java](asymmectric-encryption/src/main/java/com/doph/user/UserMapper.java).

| Method | Nhiệm vụ |
|---|---|
| `toUser(RegistrationRequest)` | Tạo entity, băm password, đặt các cờ ban đầu |
| `mergerUserInfo(User, ProfileUpdateRequest)` | Sửa first/last name trên entity truyền vào |
| `toUserResponse(User)` | Chỉ lấy field cần trả client, đổi role thành danh sách tên |

`toUser` đặt `enabled=true`, `locked=false`, `credentialsExpired=false`, `emailVerified=false`, `phoneVerified=false`. Đây là **logic mapper tự gán**, khác với UUID do JPA sinh hay thời gian do listener gán. Builder không tự quyết định các giá trị nghiệp vụ này.

`mergerUserInfo` dùng `StringUtils.isNotBlank`, chỉ cập nhật tên khi có giá trị không trắng và khác hiện tại. Gửi `null`, `""`, hoặc chuỗi toàn khoảng trắng không xóa được tên. `dateOfBirth` có trong `ProfileUpdateRequest` nhưng chưa được method này gán vào entity; gửi ngày sinh hiện không làm thay đổi dữ liệu.

### 5.5. Từng method nghiệp vụ tài khoản

| Method | Các bước thực tế |
|---|---|
| `updateProfileInformation` | Tìm user theo ID → không có thì `USER_NOT_FOUND` → mapper cập nhật tên → `save` |
| `changePassword` | So new/confirm → tải user → `matches(current, hash)` → encode mật khẩu mới → gán → save |
| `deactivateAccount` | Tải user → nếu đã tắt thì báo lỗi → `enabled=false` → save |
| `reactivateAccount` | Tải user → nếu đã bật thì báo lỗi → `enabled=true` → save |
| `deleteAccount` | Chỉ có comment dự kiến lên lịch xóa; **chưa thực hiện xóa hoặc lập lịch** |

Đổi mật khẩu hiện chưa kiểm tra độ mạnh của mật khẩu mới. DTO này cũng chưa có constraint; `newPassword=null` có thể lỗi tại `.equals` trước khi thành business error. Đổi mật khẩu chưa thu hồi các token đã cấp.

Các method `UserServiceImpl` hiện không có transaction ở cấp service. Repository vẫn cung cấp transaction cho các thao tác tương ứng, nhưng chuỗi “đọc → kiểm tra → sửa → lưu” chưa được bao trọn trong một service transaction như `register`. Phần 19 giải thích khác biệt.

<a id="buoc-6"></a>
## 6. Xây `UserController` — principal, DTO và HTTP status

File: [UserController.java](asymmectric-encryption/src/main/java/com/doph/user/UserController.java).

`@RestController` đánh dấu controller và ghi giá trị trả về vào response body; `@RequestMapping("/api/v1/users")` đặt tiền tố. `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping` ghép HTTP method với đường dẫn con. `@RequestBody` chuyển JSON thành object; `@Valid` yêu cầu validate object đó. `@ResponseStatus` định status cho lần trả về bình thường; exception handler có thể quyết định status khi có lỗi.

| HTTP | Endpoint | Response khi method thành công | Thực tế |
|---|---|---|---|
| GET | `/api/v1/users/me` | `200` + `UserResponse` | Lấy user từ principal rồi map |
| PATCH | `/api/v1/users/me` | `204`, không body | Sửa tên; chưa sửa ngày sinh |
| POST | `/api/v1/users/me/password` | `204`, không body | Đổi hash mật khẩu |
| PATCH | `/api/v1/users/me/deactivate` | `204`, không body | Tắt cờ enabled |
| PATCH | `/api/v1/users/me/activate` | `204`, không body | Bật cờ enabled |
| DELETE | `/api/v1/users/me` | Thông thường `200`, body rỗng | Gọi service rỗng; user chưa bị xóa |

### 6.1. `Principal` là gì? Không phải “principle”

Tên đúng trong Java Security là **`Principal`**, nghĩa là chủ thể/danh tính. `java.security.Principal` là interface có `getName()`. `Authentication` của Spring Security kế thừa `Principal` và bổ sung principal object, credentials, authorities, trạng thái authenticated cùng details.

Trong filter của dự án, principal object được đặt là đối tượng `User`. Vì vậy controller dùng:

```java
final User user = (User) authentication.getPrincipal();
final String userId = user.getId();
```

Ép kiểu này hoạt động do luồng hiện tại tạo principal là `User`; nó không phải bảo đảm áp dụng cho mọi cơ chế Security. Nếu sau này dùng principal là `Jwt`, `String` hoặc custom object khác, phép ép kiểu phải thay đổi.

| Biểu thức | Giá trị trong dự án |
|---|---|
| `authentication.getPrincipal()` | Đối tượng `User` đã tải từ DB |
| `authentication.getName()` | Tên định danh, với principal `UserDetails` này là email |
| `((User) authentication.getPrincipal()).getId()` | UUID của user |
| `authentication.getAuthorities()` | Các authority như `ROLE_USER` |
| `authentication.getCredentials()` | Filter JWT đặt `null`; không phải chuỗi token |
| `authentication.getDetails()` | Chi tiết request do `WebAuthenticationDetailsSource` tạo |

Spring MVC lấy `Authentication` cho tham số controller từ cơ chế tích hợp Security với request. Client không được gửi JSON tự khai `Authentication` để quyết định mình là ai. Endpoint `/me` lấy ID từ danh tính đã xác thực, giúp tránh việc client tùy ý sửa user khác bằng một ID gửi trong body. Tổng quan các kiểu này có trong [kiến trúc xác thực Spring Security](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html).

### 6.2. Vì sao không trả thẳng `User`?

`UserResponse` chỉ có ID, hồ sơ, cờ trạng thái và tên roles; không có password hash. Nó còn tránh serialize quan hệ hai chiều của entity. `GET /me` hiện dùng đối tượng đã được filter tải lên, không gọi service để tải user thêm lần nữa. Đây là dữ liệu của request hiện tại, không phải snapshot được lưu nguyên vẹn trong JWT.

<a id="buoc-7"></a>
## 7. Xây `KeyUtils`, `JwtService` — hiểu kỹ public key và private key

File: [KeyUtils.java](asymmectric-encryption/src/main/java/com/doph/security/KeyUtils.java), [JwtService.java](asymmectric-encryption/src/main/java/com/doph/security/JwtService.java).

### 7.1. Ba thao tác khác nhau: băm, mã hóa, ký số

| Thao tác | Mục đích | Ví dụ trong dự án |
|---|---|---|
| Băm mật khẩu | Lưu giá trị dùng để kiểm tra mật khẩu, không khôi phục bản gốc | `BCryptPasswordEncoder` |
| Mã hóa dữ liệu | Che nội dung; người có khóa phù hợp giải mã để đọc | Chưa có luồng mã hóa payload JWT |
| Ký số | Kiểm tra dữ liệu do bên giữ private key tạo và không bị sửa | `signWith(privateKey)` / `verifyWith(publicKey)` |

RSA có cặp khóa liên hệ toán học với nhau, nhưng phục vụ giao thức khác nhau tùy thao tác. Trong **luồng ký JWT này**, bên phát hành giữ private key để ký, bên xác minh dùng public key kiểm tra chữ ký. Không nên mô tả là “server mã hóa token bằng private key rồi client giải mã bằng public key”: cách nói đó làm nhầm tính xác thực với tính bí mật.

### 7.2. Ai giữ khóa, ai giữ token?

```mermaid
sequenceDiagram
    participant C as Client
    participant A as API phát hành token
    participant V as API xác minh token
    C->>A: email + password qua HTTPS
    A->>A: Xác thực tài khoản
    A->>A: Dùng private key ký JWT
    A-->>C: access_token + refresh_token
    C->>V: Authorization: Bearer access_token
    V->>V: Dùng public key kiểm tra chữ ký + claims
    V-->>C: Kết quả API nếu được phép
```

Trong dự án, bên phát hành và bên xác minh đều nằm trong cùng ứng dụng, `JwtService` nạp cả hai khóa. Khi tách nhiều service, service chỉ cần xác minh có thể giữ public key mà không được quyền phát hành token. Client thông thường chỉ cần giữ token, không cần private key để gọi API. Public key có thể chia sẻ nhưng phải lấy từ nguồn tin cậy; bị thay public key sẽ phá vỡ cơ sở tin cậy của việc xác minh.

Ai có private key phù hợp có thể ký token mà bộ xác minh chấp nhận về mặt chữ ký. Vì thế thư mục `keys/local-only` chỉ phù hợp học/demo. Hiện cả private key và public key có trong danh sách file được Git theo dõi, và resource có thể được đóng gói vào ứng dụng. Khi triển khai thật cần thay khóa demo và quản lý private key ngoài source/artifact công khai; không đưa nội dung khóa vào README hay response API.

### 7.3. Cấu trúc JWT được dự án tạo

JWT ký dạng compact gồm ba phần phân tách bằng dấu chấm:

```text
base64url(header).base64url(payload).base64url(signature)
```

Header/payload có thể giải mã Base64URL để xem nội dung, không cần private key. Đọc được JSON không đồng nghĩa chữ ký hợp lệ. Payload minh họa, không phải token thực:

```json
{
  "token_type": "ACCESS_TOKEN",
  "sub": "learner@example.com",
  "iat": 1795190400,
  "exp": 1795276800
}
```

| Claim | Ý nghĩa trong code |
|---|---|
| `sub` | Subject = username = email |
| `iat` | Thời điểm phát hành |
| `exp` | Hạn dùng |
| `token_type` | Claim tự định nghĩa: `ACCESS_TOKEN` hoặc `REFRESH_TOKEN` |

Các số JSON trên chỉ minh họa giây Unix. `JwtService` truyền `java.util.Date`, còn giá trị cấu hình expiration được code cộng theo **milliseconds**. JWT hiện không chứa roles, user ID, `issuer`, `audience`, `jti` hay token version. Quyền được đọc từ database trong filter, không lấy từ claim roles.

### 7.4. PEM, DER, PKCS#8 và X.509

File PEM là văn bản có header/footer và phần Base64 của dữ liệu nhị phân. `KeyUtils` bỏ header/footer, bỏ whitespace, Base64-decode thành byte DER, rồi yêu cầu Java tạo đối tượng khóa RSA.

| Khóa | Header mà code xử lý | Key spec Java |
|---|---|---|
| Private | `-----BEGIN PRIVATE KEY-----` | `PKCS8EncodedKeySpec` |
| Public | `-----BEGIN PUBLIC KEY-----` | `X509EncodedKeySpec` |

PKCS#8 ở đây chứa thông tin private key. X.509 key spec ở đây là cấu trúc `SubjectPublicKeyInfo` của public key; file `PUBLIC KEY` không phải một chứng chỉ X.509 hoàn chỉnh có CA ký. Dùng `PKCS8EncodedKeySpec` để đọc public key là sai định dạng. Xem [PKCS8EncodedKeySpec](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/PKCS8EncodedKeySpec.html) và [X509EncodedKeySpec](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/spec/X509EncodedKeySpec.html).

Ví dụ tạo **cặp demo mới** bằng OpenSSL nếu đã cài công cụ này, chạy từ thư mục module. Các tên file dưới đây khác tên khóa đang dùng:

```powershell
openssl genpkey -algorithm RSA -out demo_private_key.pem -pkeyopt rsa_keygen_bits:2048
openssl pkey -in demo_private_key.pem -pubout -out demo_public_key.pem
```

Chỉ chạy khi hai file đích demo chưa tồn tại. Public key phải được suy ra từ **chính private key vừa tạo**, không tạo độc lập một cặp khác. Lệnh chỉ sinh file, không tự thay khóa ứng dụng; muốn dùng cần cập nhật vị trí/đường dẫn nạp một cách chủ động. Code hiện không xử lý PEM dạng `BEGIN RSA PRIVATE KEY` hay private key được mã hóa có passphrase.

### 7.5. Đọc từng bước của `KeyUtils`

```java
final String key = readKeyFromResource(pemPath)
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replaceAll("\\s+", "");
final byte[] decoded = Base64.getDecoder().decode(key);
final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
return KeyFactory.getInstance("RSA").generatePrivate(spec);
```

`KeyFactory` biến mô tả khóa thành đối tượng `PrivateKey`/`PublicKey`; nó không sinh cặp khóa mới trong đoạn này. `static` cho phép gọi `KeyUtils.loadPrivateKey(...)` không cần tạo bean. Constructor `private KeyUtils()` ngăn việc tạo instance utility theo cách thông thường.

`KeyUtils.class.getClassLoader().getResourceAsStream(pemPath)` đọc từ **classpath**, không phải đường dẫn tuyệt đối tùy ý trên ổ đĩa. Resource `src/main/resources/keys/local-only/public_key.pem` được tham chiếu là `keys/local-only/public_key.pem`. Dùng `Class.getResourceAsStream` có quy tắc path tương đối theo package hoặc path tuyệt đối bắt đầu `/`, khác với classloader API đang dùng.

`try (InputStream is = ...)` là try-with-resources, tự đóng stream. Không tìm thấy file thì code ném `IllegalArgumentException`. Nếu gặp `IOException`, code hiện `printStackTrace()` rồi trả `null`; lời gọi `.replace` sau đó có thể phát sinh `NullPointerException`, che nguyên nhân. Hướng cải thiện là ném exception có nguyên nhân gốc và fail rõ lúc startup. `new String(is.readAllBytes())` đang dùng charset mặc định; có thể chỉ định UTF-8 rõ ràng.

**Điểm cần chuẩn hóa:** constructor `JwtService` hiện nạp private key bằng `"keys//local-only/private_key.pem"`, còn public key dùng `"keys/local-only/public_key.pem"`. Double slash là lệch đường dẫn cần sửa về một dấu `/`; việc nó có được chuẩn hóa hay không tùy cách resource được cung cấp, không nên dựa vào hành vi của thư mục local để suy ra chạy JAR cũng đúng.

### 7.6. Đọc từng method của `JwtService`

`@Service` giúp Spring quản lý service. Constructor nạp khóa một lần khi tạo bean. `@Value` inject hai thời hạn vào field sau giai đoạn tạo instance; constructor hiện không dùng hai field đó. Đường dẫn khóa đang hardcode, không lấy từ `application.yml`.

```java
return Jwts.builder()
    .claims(claims)
    .subject(username)
    .issuedAt(new Date(System.currentTimeMillis()))
    .expiration(new Date(System.currentTimeMillis() + expiration))
    .signWith(this.privateKey)
    .compact();
```

`buildToken` gom quá trình tạo claims, đặt subject/thời gian, ký và xuất chuỗi. `signWith(privateKey)` cho JJWT chọn thuật toán phù hợp với khóa; code chưa chỉ định tường minh `RS256`, nên không ghi cứng mọi token chắc chắn dùng thuật toán đó. `generateAccessToken` truyền loại ACCESS và thời hạn access; `generateFreshToken` — tên method thực tế, dù thường người ta đặt `generateRefreshToken` — truyền loại REFRESH và thời hạn refresh.

```java
return Jwts.parser()
    .verifyWith(this.publicKey)
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

`extractClaims` dùng parser xác minh JWS rồi lấy payload. `extractUsername` gọi nó để lấy subject, do đó **không chỉ Base64-decode**. `isTokenValid` so subject với username mong đợi và kiểm tra chưa hết hạn. Nó hiện chưa kiểm tra loại token, issuer hay audience. Mỗi lần gọi các helper này có thể parse/xác minh lại token, chưa tái sử dụng claims.

JJWT có thể ném exception ngay khi parse token hết hạn hoặc sai chữ ký. `extractClaims` bọc `JwtException` thành `JwtException("Invalid JWT token", e)`. Vì vậy các nhánh `isTokenExpired(...)` không bảo đảm luôn trả boolean cho token hết hạn: parser có thể ném lỗi trước đó. Đây là lý do phải thiết kế luồng exception thay vì chỉ trông chờ `if (expired)`. Đối chiếu API tại [JJWT 0.12.6](https://github.com/jwtk/jjwt/blob/0.12.6/README.adoc).

### 7.7. Access token và refresh token khác mục đích

| Loại | Mục đích dự kiến | Thời hạn code hiện tại |
|---|---|---|
| Access | Gọi API được bảo vệ | 24 giờ |
| Refresh | Xin access token mới qua `/auth/refresh` | 7 ngày |

`refreshAccessToken` parse claims → yêu cầu `token_type=REFRESH_TOKEN` → kiểm tra hạn → lấy subject → tạo access token mới. `AuthenticationServiceImpl.refreshToken` trả lại refresh token cũ, không rotation, không gia hạn `exp` của refresh token. Access token mới có thời hạn riêng tính từ lúc phát hành; code không giới hạn nó bằng thời gian còn lại của refresh token.

Không có bảng lưu token, danh sách thu hồi hoặc endpoint logout trong mã nguồn. Xóa token ở client chỉ bỏ bản sao ở client đó. Đổi password hay deactivate không tự làm chữ ký token cũ vô hiệu. Cần phân biệt “token còn hợp lệ về chữ ký/hạn” với “tài khoản hiện được phép hoạt động”.

<a id="buoc-8"></a>
## 8. Xây `JwtFilter` — authentication cho mỗi request

File: [JwtFilter.java](asymmectric-encryption/src/main/java/com/doph/security/JwtFilter.java).

### 8.1. Vì sao kế thừa `OncePerRequestFilter`?

`JwtFilter extends OncePerRequestFilter` tái sử dụng hạ tầng servlet filter của Spring; bạn override `doFilterInternal`. Lớp nền quản lý dấu hiệu đã lọc và các quy tắc dispatch để tránh lặp không cần thiết. Không nên hiểu tên lớp là bảo đảm tuyệt đối “một lần cho mọi async/error dispatch” mà không xét cấu hình dispatch.

`@Component` đăng ký filter bean. `@RequiredArgsConstructor` inject `JwtService` và `UserDetailsService`; bean thực thi hợp đồng sau là `UserServiceImpl`. `@NonNull` ở tham số là annotation Lombok về null, không phải Jakarta validation của JSON request.

### 8.2. Truy vết thuật toán hiện tại

1. Nếu servlet path bắt đầu `/api/v1/auth`, gọi tiếp chain rồi `return`: không đọc Bearer token cho các request này.
2. Lấy header `Authorization` bằng `HttpHeaders.AUTHORIZATION`.
3. Không có header hoặc không bắt đầu bằng `"Bearer"` thì chuyển tiếp. Đây không phải chấp nhận request; rule phía sau vẫn có thể từ chối nếu URL cần authentication.
4. `substring(7)` lấy chuỗi JWT với giả định header là `Bearer ` + token.
5. `extractUsername(jwt)` parse/xác minh rồi lấy email.
6. Chỉ xử lý tiếp khi username không null và context chưa có authentication.
7. `loadUserByUsername(username)` tải user và roles từ database.
8. `isTokenValid(jwt, userDetails.getUsername())` so subject và hạn.
9. Tạo authentication với principal user, credentials null, authorities từ user.
10. Thêm request details, đặt authentication vào context, rồi gọi phần còn lại của chain.

Đoạn đánh dấu danh tính cho request:

```java
final UsernamePasswordAuthenticationToken authToken =
    new UsernamePasswordAuthenticationToken(
        userDetails,
        null,
        userDetails.getAuthorities()
    );
authToken.setDetails(
    new WebAuthenticationDetailsSource().buildDetails(request)
);
SecurityContextHolder.getContext().setAuthentication(authToken);
```

Constructor ba tham số tạo token được xem là authenticated. Filter đang tự đưa ra quyết định tin cậy sau kiểm tra JWT; không gọi lại manager để kiểm tra password. `WebAuthenticationDetailsSource` cung cấp chi tiết như remote address và session ID nếu có; nó không tự kiểm tra IP để cấp quyền và không biến ứng dụng thành session login.

```text
SecurityContextHolder
└── SecurityContext
    └── Authentication
        ├── principal: User
        ├── credentials: null
        ├── authorities: [ROLE_USER, ...]
        └── details: thông tin request
```

### 8.3. Các giới hạn phải hiểu khi đọc filter này

| Điểm trong code | Hành vi có thể xảy ra | Hướng cải thiện chưa áp dụng |
|---|---|---|
| `startsWith("Bearer")`, sau đó `substring(7)` | Chấp nhận tiền tố thiếu dấu cách; header quá ngắn có thể gây lỗi | Kiểm tra đầy đủ scheme và token không rỗng |
| `isTokenValid` không kiểm tra `token_type` | Refresh token có subject/hạn hợp lệ cũng có thể được dùng gọi API | Endpoint tài nguyên chỉ chấp nhận ACCESS |
| Không kiểm tra `enabled`, locked, credentials expired | Token cũ có thể tiếp tục truy cập sau deactivate/khóa | Kiểm tra trạng thái user trước khi đặt context |
| Không bắt lỗi parse/load user | JWT hết hạn/sai hoặc user đã xóa có thể ném lỗi ngoài MVC | Xử lý lỗi tại tầng filter/entry point phù hợp |
| Gọi nhiều helper parse token | Một request có thể xác minh cùng token nhiều lần | Parse một lần rồi dùng claims đã xác minh |
| Chưa có `issuer`/`audience` policy | Chưa giới hạn token theo bên phát hành/bên nhận cụ thể | Bổ sung khi thiết kế môi trường triển khai |

Lưu ý lỗi đầu tiên ảnh hưởng cả URL `permitAll` ngoài nhánh auth: chúng vẫn đi qua filter, nên một Bearer header hỏng có thể làm request thất bại trước khi tới rule cho phép.

Đăng nhập bằng password qua provider có kiểm tra trạng thái tài khoản, còn đường đi filter này đang tự dựng authenticated token nên không được thừa hưởng mọi kiểm tra của provider. Đây là sự khác nhau quan trọng nhất khi phân tích deactivate: hiện người dùng có thể còn gọi API bằng token cũ, nhưng không đăng nhập mới bằng tài khoản đã bị tắt. Sau khi bổ sung kiểm tra `enabled` vào JWT filter, cần thiết kế lại cách kích hoạt tài khoản; không thể dựa vào token tài khoản bị tắt để tự gọi `/me/activate` như trước.

Về đăng ký filter: Spring Boot có thể tự đăng ký một bean `Filter` với servlet container, trong khi code cũng chèn nó vào SecurityFilterChain. Khi kiểm tra thứ tự chạy thực tế, cần kiểm tra cả đăng ký container và chain; nếu chỉ muốn filter chạy trong Security chain có thể dùng `FilterRegistrationBean` để tắt đăng ký container. Đây là lưu ý về tích hợp, chưa phải kết luận đã quan sát filter chạy hai lần trong ứng dụng này.

<a id="buoc-9"></a>
## 9. Tạo DTO xác thực — request/response không phải entity

DTO là object chuyên chở dữ liệu qua ranh giới API. Chúng không có `@Entity`, không tự tạo bảng, không tự lưu database. File nằm trong `auth/request`, `auth/response`, `user/request`, `user/response`.

| Class | Field / mục đích | Validation đang có |
|---|---|---|
| `AuthenticationRequest` | `email`, `password` cho login | Email bắt buộc, đúng dạng; password không trắng |
| `RegistrationRequest` | first/last name, email, phone, password, confirm | Constraint chi tiết ở bước 13–14 |
| `RefreshRequest` | `refreshToken` | Chưa có constraint và endpoint chưa có `@Valid` |
| `AuthenticationResponse` | access, refresh, token type | Chỉ là response |
| `ProfileUpdateRequest` | first/last name, `dateOfBirth` | Chưa có constraint trên field |
| `ChangePasswordRequest` | current/new/confirm password | Chưa có constraint trên field |
| `UserResponse` | Hồ sơ và tên role, không có password | Chỉ là response |

DTO dùng `@Getter`, `@Setter`, `@AllArgsConstructor`, `@NoArgsConstructor`, `@Builder` để giảm code lặp. Jackson thường dùng constructor rỗng cùng setter để dựng request. Builder phục vụ nơi ứng dụng tự tạo object; không cần yêu cầu client gửi JSON theo “cú pháp builder”.

### 9.1. Phân biệt camelCase request và snake_case response

`AuthenticationResponse` có:

```java
@JsonProperty("access_token")
private String accessToken;
@JsonProperty("refresh_token")
private String refreshToken;
@JsonProperty("token_type")
private String tokenType;
```

Do đó response có dạng:

```json
{
  "access_token": "<JWT access>",
  "refresh_token": "<JWT refresh>",
  "token_type": "Bearer"
}
```

Nhưng `RefreshRequest` không có annotation đổi tên; body hiện cần **`refreshToken`**:

```json
{ "refreshToken": "<JWT refresh>" }
```

Hai từ `token_type` cũng có hai ngữ cảnh: trong response nó là scheme `Bearer`; trong JWT payload nó là `ACCESS_TOKEN`/`REFRESH_TOKEN` để biểu thị mục đích. Chúng không phải một giá trị chung.

<a id="buoc-10"></a>
## 10. Xây nghiệp vụ xác thực — `AuthenticationServiceImpl`

File: [AuthenticationServiceImpl.java](asymmectric-encryption/src/main/java/com/doph/auth/impl/AuthenticationServiceImpl.java).

`AuthenticationService` là interface có `login`, `register`, `refreshToken`; `AuthenticationServiceImpl` là implementation được `@Service` đăng ký. `@Slf4j` sinh logger `log`. Constructor do Lombok tạo nhận manager, JWT service, hai repository và mapper.

### 10.1. Đăng nhập: manager, provider và user details nối với nhau ra sao?

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant AS as AuthenticationServiceImpl
    participant AM as AuthenticationManager / Provider
    participant UD as UserServiceImpl
    participant DB as UserRepository / DB
    participant JS as JwtService
    C->>AC: POST /auth/login (email, password)
    AC->>AC: Deserialize và validate DTO
    AC->>AS: login(request)
    AS->>AM: authenticate(token 2 tham số)
    AM->>UD: loadUserByUsername(email)
    UD->>DB: findByEmailIgnoreCase(email)
    DB-->>AM: UserDetails chứa hash và trạng thái
    AM->>AM: Kiểm tra trạng thái + BCrypt.matches
    AM-->>AS: Authentication chứa principal User
    AS->>JS: generateAccessToken + generateFreshToken
    JS-->>AS: Hai chuỗi JWT đã ký
    AS-->>C: 200 + AuthenticationResponse
```

Phần manager/provider trong sơ đồ là cơ chế chuẩn khi Spring cấu hình username/password với `UserDetailsService` và encoder. Project lấy manager qua `AuthenticationConfiguration`; chưa tự khai báo một custom `AuthenticationProvider`. Chưa chạy tích hợp nên không khẳng định đã quan sát danh sách provider ở runtime. Cơ chế đối chiếu là [DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html).

```java
final Authentication auth = authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(
        request.getEmail(), request.getPassword()
    )
);
final User user = (User) auth.getPrincipal();
```

Constructor **hai tham số** tạo yêu cầu xác thực, principal ban đầu là email và credentials là password thô. Manager chọn provider phù hợp. Provider tải `UserDetails`, kiểm tra các điều kiện tài khoản và so mật khẩu bằng encoder. Kết quả thành công chứa principal là user, dùng để lấy username ký token.

Manager không tự sinh JWT; đó là trách nhiệm `JwtService`. `UserDetailsService` không tự xác nhận mật khẩu đúng; nó cung cấp dữ liệu cho provider. Tự tạo `new UsernamePasswordAuthenticationToken(email, password)` cũng chưa có nghĩa đăng nhập thành công.

`login` hiện dùng `auth` như biến cục bộ; không gọi `SecurityContextHolder.setAuthentication`. API trả token cho client dùng trong request sau, không thiết lập một phiên đăng nhập được lưu lại chỉ bằng việc controller đã gọi manager.

### 10.2. Đăng ký: dữ liệu được tạo ở đâu?

Luồng `register` hiện tại:

1. `checkUserEmail`: hỏi repository; nếu đã có email thì ném `EMAIL_ALREADY_EXISTS`.
2. `checkUserPhoneNumber`: nếu trùng phone thì `PHONE_ALREADY_EXISTS`.
3. `checkPasswords`: password null hoặc khác confirm thì `PASSWORD_MISMATCH`.
4. Tìm role tên chính xác `ROLE_USER`; thiếu role thì ném `EntityNotFoundException`.
5. Tạo danh sách role và gọi `userMapper.toUser(request)` để tạo user, hash password, gán các cờ.
6. `user.setRoles(roles)` thiết lập owning side của many-to-many.
7. `userRepository.save(user)`: JPA quản lý việc ghi user; UUID/audit được gán theo lifecycle.
8. Tạo một danh sách users chỉ chứa user mới, `userRole.setUsers(users)`, rồi `roleRepository.save(userRole)`.

Bước 8 không phải thao tác bắt buộc để bảng nối được lưu, vì owning side là `User.roles`. Nó còn thay toàn bộ danh sách inverse trong bộ nhớ bằng danh sách chỉ có người mới, không phản ánh các user cũ. Không được suy ra bước này tự xóa mọi user cũ khỏi database: quan hệ được quyết định ở owning side và code chưa có orphan removal cho trường hợp này. Khi cải thiện có thể quản lý hai phía bằng helper `addRole` hoặc chỉ thao tác phía sở hữu theo nhu cầu.

### 10.3. `@Transactional` trên đăng ký

Code dùng **`jakarta.transaction.Transactional`**. Spring hỗ trợ annotation này và bao lời gọi service qua transaction interceptor. Nếu luồng thành công, transaction được commit; khi gặp unchecked exception phù hợp chính sách mặc định, rollback. Nhờ vậy nhiều thao tác persistence trong đăng ký được gom vào một đơn vị công việc.

`save` không bảo đảm mọi câu SQL chạy ngay ở dòng đó. Hibernate có thể chờ flush/commit để đồng bộ; lỗi unique hoặc constraint có thể xuất hiện vào thời điểm flush. Kiểm tra `exists...` trước giúp báo lỗi tốt hơn nhưng database constraint vẫn cần để chống ghi đồng thời. Annotation không tự sửa quy tắc unique email không phân biệt hoa thường.

Khi gọi phương thức từ bên ngoài qua Spring proxy, interceptor mới có cơ hội mở transaction. Tự gọi method trong cùng instance không đi qua proxy theo cơ chế thông thường. Không sao chép thuộc tính như `readOnly=true` vào annotation Jakarta này: đó là thuộc tính của `org.springframework.transaction.annotation.Transactional`, một kiểu annotation khác.

### 10.4. Refresh: kiểm token nhưng chưa kiểm trạng thái user

`refreshToken` chuyển chuỗi refresh cho `JwtService.refreshAccessToken`, nhận access mới, trả refresh cũ. Nó chưa tải user để kiểm tra `enabled`, locked, tài khoản còn tồn tại hay mật khẩu vừa thay. Vì vậy refresh token còn chữ ký/hạn hợp lệ vẫn có thể tạo access token sau khi tắt tài khoản; sau đó việc dùng access token còn phụ thuộc logic ở filter.

Code cũng chưa có refresh-token rotation hoặc phát hiện tái sử dụng refresh token. Nếu bổ sung, thường cần lưu định danh token/phiên, trạng thái thu hồi, hạn, và thay token cũ bằng token mới trong một transaction phù hợp. Đây là hướng thiết kế, chưa phải tính năng dự án.

<a id="buoc-11"></a>
## 11. Xây `AuthController` — ranh giới HTTP của xác thực

File: [AuthController.java](asymmectric-encryption/src/main/java/com/doph/auth/AuthController.java).

| Endpoint | DTO vào | Kết quả thành công | Validation tại controller |
|---|---|---|---|
| `POST /api/v1/auth/register` | `RegistrationRequest` | `201 Created`, không body | Có `@Valid` |
| `POST /api/v1/auth/login` | `AuthenticationRequest` | `200 OK`, hai token | Có `@Valid` |
| `POST /api/v1/auth/refresh` | `RefreshRequest` | `200 OK`, access mới + refresh cũ | Chưa có `@Valid` |

`ResponseEntity.ok(body)` đặt status 200 và body; `ResponseEntity.status(CREATED).build()` trả 201 không body. `ResponseEntity<Void>` diễn đạt response không có dữ liệu body. Đăng ký chưa tự login, chưa trả token và chưa gửi email xác minh.

Controller nên mỏng: nhận/validate input, gọi service, chọn dạng HTTP response. Nghiệp vụ kiểm trùng và hash password nằm ở service/mapper; không đặt SQL trong controller. Các endpoint này công khai để người chưa đăng nhập có thể đăng ký/đăng nhập hoặc xuất trình refresh token.

<a id="buoc-12"></a>
## 12. Auditing và listener — ai tự gán giá trị lúc tạo, sửa?

File: [JpaConfig.java](asymmectric-encryption/src/main/java/com/doph/config/JpaConfig.java), [BeanConfig.java](asymmectric-encryption/src/main/java/com/doph/config/BeanConfig.java), [ApplicationAuditorAware.java](asymmectric-encryption/src/main/java/com/doph/config/ApplicationAuditorAware.java).

### 12.1. Ba thành phần phối hợp

```text
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
    ├── bật hạ tầng auditing
    └── chọn bean tên auditorAware

@EntityListeners(AuditingEntityListener.class)
    └── nhận lifecycle callback trên entity

@CreatedDate / @LastModifiedDate / @CreatedBy / @LastModifiedBy
    └── chỉ định field cần gán
```

`AuditingEntityListener` lắng nghe sự kiện persistence trước khi tạo/cập nhật entity để gán metadata. Nó không phải HTTP filter, không phải listener bắt mọi thay đổi database từ mọi ứng dụng. `AuditorAware<String>` trả về danh tính người thao tác cho field kiểu String. Xem [Spring Data JPA Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html).

### 12.2. Bean đang dùng khác lớp có sẵn

Bean **đang hoạt động** trong `BeanConfig`:

```java
@Bean
public AuditorAware<String> auditorAware() {
    return () -> Optional.ofNullable(
            SecurityContextHolder.getContext().getAuthentication())
        .filter(auth -> auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken))
        .map(Authentication::getName)
        .or(() -> Optional.of("SYSTEM"));
}
```

Đọc từ trái sang phải: lấy authentication có thể null → bỏ qua khi chưa xác thực hoặc là anonymous → lấy tên → nếu không có thì dùng `SYSTEM`. Với principal `User`, `Authentication.getName()` là email, nên `createdBy/lastModifiedBy` lưu **email hoặc `SYSTEM`**, không phải UUID.

`AnonymousAuthenticationToken` là danh tính khách do framework có thể cung cấp, khác người dùng thật; chỉ kiểm tra `isAuthenticated()` là chưa đủ để phân biệt nó. `Optional.or` chỉ chạy supplier dự phòng khi Optional đang rỗng.

Lớp `ApplicationAuditorAware` có implementation khác: ép principal sang `User`, trả UUID, và trả `Optional.empty()` khi không có user hợp lệ. Nhưng nó không có stereotype đăng ký bean, còn `@Bean` dùng `new ApplicationAuditorAware()` đã được comment. Vì vậy không được lấy hành vi của lớp này để mô tả auditing đang chạy. Nếu kích hoạt nó mà không có fallback, thao tác tạo role lúc startup có thể không có auditor trong khi `createdBy` của `BaseEntity` là `nullable=false`.

### 12.3. Vai trò của từng audit annotation và `@Column`

| Field | Annotation | Lúc tạo | Lúc cập nhật |
|---|---|---|---|
| `createdDate` | `@CreatedDate`, `updatable=false`, `nullable=false` | Listener gán thời gian; tham gia INSERT | Không đưa vào SQL UPDATE |
| `lastModifiedDate` | `@LastModifiedDate`, `insertable=false` | Listener có thể gán trong bộ nhớ; cột bị loại khỏi INSERT | Có thể ghi thời gian sửa khi UPDATE |
| `createdBy` ở BaseEntity | `@CreatedBy`, `updatable=false`, `nullable=false` | Gán email hoặc SYSTEM | Không đưa vào SQL UPDATE |
| `lastModifiedBy` ở BaseEntity | `@LastModifiedBy`, `insertable=false` | Có thể được gán trong bộ nhớ; không ghi INSERT | Gán người sửa và đưa vào UPDATE |

Mặc định auditing có thể đánh dấu cả thông tin modified khi tạo (`modifyOnCreate`); nhưng `insertable=false` vẫn khiến JPA không ghi cột đó trong INSERT. Không có default/trigger riêng thì cột modified trong database có thể null sau tạo, dù object đang giữ giá trị. `insertable=false` không có nghĩa field bị cấm gán trong Java. `updatable=false` cũng không làm Java object bất biến và không ngăn câu SQL viết tay ngoài ORM thay đổi cột.

### 12.4. Ví dụ thời gian thực tế

**Startup tạo role:** runner → repository.save(role) → listener → sinh UUID, `createdDate`, `createdBy=SYSTEM` vì chưa có người đăng nhập. `Role` nhận field từ `BaseEntity`.

**Đăng ký public:** filter bỏ qua auth endpoint → không có user authentication do luồng đăng ký đặt vào context → mapper tạo User → JPA sinh ID và ngày tạo. `User` không có createdBy nên không lưu SYSTEM ở một field không tồn tại. Role hiện có có thể được cập nhật trong transaction nếu có thay đổi thực sự; gọi `save` trên một đối tượng không đổi không phải lúc nào cũng phát sinh UPDATE/listener sửa.

**Sửa hồ sơ qua JWT:** filter dựng authentication → service sửa tên → JPA nhận thay đổi → listener cập nhật `lastModifiedDate`. Nếu một entity kế thừa `BaseEntity` được sửa trong cùng request đó, auditor lấy email cho `lastModifiedBy`.

### 12.5. Giới hạn của tự động auditing

Auditing không chạy chỉ vì client gửi request hoặc vì Java object được `new`. Nó phụ thuộc lifecycle persistence. JPQL bulk update/native SQL hoặc sửa trực tiếp trong database thường không đi qua listener cho từng entity. Gán lại đúng giá trị cũ không bảo đảm có SQL UPDATE và cũng không bảo đảm modified timestamp đổi. Auditing này chỉ lưu người/thời điểm gần nhất, không lưu lịch sử đầy đủ trước–sau của mọi lần sửa.

Nếu xử lý trong scheduled job/thread khác mà không có SecurityContext, bean hiện trả SYSTEM. Nếu muốn lưu ID ổn định thay email, cần đổi implementation auditor và cân nhắc dữ liệu cũ, không chỉ đổi lời giải thích. Email có thể thay đổi theo nghiệp vụ; dự án hiện cũng chưa có API đổi email.

<a id="buoc-13"></a>
## 13. Validation và `@Pattern` — giải thích quy tắc Java

### 13.1. Annotation không phải điều kiện SQL

Validation request thường diễn ra sau khi JSON được chuyển thành DTO, trước khi method controller thực thi. Các constraint của field mô tả điều kiện; `@Valid` trên tham số yêu cầu framework kiểm tra object. `@Valid` không tự sinh điều kiện nếu DTO không có constraint.

| Annotation | Kiểm tra | Lưu ý trong dự án |
|---|---|---|
| `@NotBlank` | String không null và không toàn khoảng trắng | Phù hợp email/password bắt buộc |
| `@Email` | Định dạng email | Không chứng minh email tồn tại hay đã xác minh |
| `@Size(min,max)` | Độ dài String hoặc kích thước collection tùy kiểu | Không tự thay thế `@NotBlank` |
| `@Pattern(regexp=...)` | String khớp regex | Không tự trim hay sửa nội dung |
| `@Valid` | Yêu cầu kiểm tra các constraint của object | Profile/change password hiện thiếu constraint |
| `@Schema(example=...)` | Tài liệu minh họa | Không phải validation |

Nhiều constraint như `@Size`, `@Pattern`, `@Email` cho phép null, nên thường phối hợp với `@NotBlank`. Không có thứ tự mặc định bảo đảm `@Email` chạy và “chặn” mọi validator khác trước; validator tự tạo phải chịu được input chưa hợp lệ. Xem [Jakarta Validation](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1).

### 13.2. Hai lớp cú pháp: Java string và regex

Code Java viết `"\\d"` để regex nhận `\d`. Một dấu `\` cần escape trong string Java. Nếu dùng thử trên công cụ regex trực tiếp, nhập `\d`, không nhập nguyên chuỗi `"\\d"` có escape của Java.

```java
// Chuỗi trong source Java
String regex = "^\\+[0-9]{10,13}";
// Regex engine nhìn thấy: ^\+[0-9]{10,13}
```

`@Pattern` của Jakarta Validation là annotation constraint; `java.util.regex.Pattern` là lớp biên dịch regex của Java. Chúng liên quan nhau nhưng không cùng một kiểu.

### 13.3. Regex tên: `"^[\\p{L} '-]+$"`

Regex engine đọc thành `^[\p{L} '-]+$`:

| Ký hiệu | Ý nghĩa |
|---|---|
| `^` | Bắt đầu input |
| `[...]` | Một ký tự thuộc tập cho phép |
| `\p{L}` | Ký tự thuộc nhóm chữ Unicode, gồm nhiều chữ tiếng Việt |
| Dấu cách, `'`, `-` | Cho phép khoảng trắng thường, nháy đơn, gạch nối |
| `+` | Ít nhất một ký tự trong tập |
| `$` | Kết thúc theo quy tắc regex |

Ví dụ `Thanh`, `Nguyễn`, `O'Neil` phù hợp về tập ký tự; `Phat123` không phù hợp. Các tên có ký tự dấu ở dạng Unicode tách rời có thể chứa combining mark không thuộc `\p{L}`, nên không nên kết luận regex chấp nhận mọi cách biểu diễn tên tiếng Việt.

Ngoài regex, code còn `@Size(min=5,max=50)` cho **cả firstName và lastName**. Vì thế ví dụ Swagger `"Do"` và `"Phat"` đang không hợp lệ do quá ngắn, dù ký tự đúng. Regex cũng không tự cấm dấu cách ở đầu/cuối, hoặc chuỗi toàn dấu gạch nối; `@NotBlank` chỉ giải quyết phần trắng. Nếu muốn chính sách tên khác cần thiết kế lại constraint, không chỉ đổi example.

### 13.4. Regex phone: `"^\\+[0-9]{10,13}"`

`^` bắt đầu; `\+` là ký tự dấu cộng thật, không phải toán tử lặp; `[0-9]` chỉ chữ số ASCII; `{10,13}` là từ 10 đến 13 chữ số **sau dấu cộng**. Ví dụ `+84987654321` có dấu cộng và 11 chữ số nên phù hợp. `0987654321` thiếu `+` nên không phù hợp.

Source không viết `$` ở cuối. Tuy nhiên constraint `@Pattern` yêu cầu toàn bộ giá trị khớp, không phải tìm một đoạn bằng `find()`, nên không được kết luận chuỗi có hậu tố chữ sẽ được nhận. Khi viết quy tắc để người đọc dễ hiểu có thể thêm `$`, nhưng cần hiểu cơ chế match hiện tại trước.

### 13.5. Regex password: `"^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*\\W).*$"`

| Thành phần | Điều kiện |
|---|---|
| `(?=.*[A-Z])` | Có ít nhất một chữ hoa ASCII |
| `(?=.*[a-z])` | Có ít nhất một chữ thường ASCII |
| `(?=.*\d)` | Có ít nhất một chữ số theo chế độ regex đang dùng |
| `(?=.*\W)` | Có ít nhất một ký tự không thuộc nhóm word |
| `.*` | Phần còn lại theo quy tắc dot của regex |

`(?=...)` là positive lookahead: kiểm tra có điều kiện phía trước mà không tiêu thụ ký tự trong kết quả khớp. Các lookahead phối hợp thành nhiều điều kiện bắt buộc. Độ dài 5–50 được kiểm tra riêng bằng `@Size`.

Ví dụ `Study@123` thỏa các ràng buộc hiện tại; `study123` thiếu hoa và ký tự đặc biệt; `STUDY@123` thiếu thường. `\W` không đơn thuần là tập `!@#$...`: khoảng trắng cũng có thể thỏa, trong khi underscore `_` là word character ở chế độ thông thường nên không thỏa riêng điều kiện `\W`. Nếu chính sách muốn cấm khoảng trắng hoặc chỉ cho phép một tập ký tự đặc biệt, phải diễn đạt cụ thể hơn.

`confirmPassword` có NotBlank và Size, chưa có annotation so sánh hai field. Service dùng `.equals` để so password/confirm. Constraint regex mật khẩu chỉ có ở đăng ký; đổi mật khẩu chưa áp dụng. Chi tiết cú pháp engine xem [Java Pattern](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html).

### 13.6. `message` có được tự dịch thành tiếng Việt không?

Các giá trị như `message="VALIDATION.REGISTRATION.EMAIL.INVALID"` là chuỗi được code khai báo. Không thấy resource bundle thông báo tương ứng trong dự án. Dạng không có `{...}` này thường được trả như chuỗi literal, không tự được coi là khóa dịch. Muốn dùng message bundle có thể đặt `{validation.email.invalid}` và cung cấp tài nguyên phù hợp; đó là phần chưa làm.

<a id="buoc-14"></a>
## 14. Tự tạo annotation — `NonDisposableEmail` và `EmailDomainValidator`

File: [NonDisposableEmail.java](asymmectric-encryption/src/main/java/com/doph/validation/NonDisposableEmail.java), [EmailDomainValidator.java](asymmectric-encryption/src/main/java/com/doph/validation/EmailDomainValidator.java).

### 14.1. Annotation định nghĩa hợp đồng, validator viết thuật toán

```java
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EmailDomainValidator.class)
public @interface NonDisposableEmail {
    String message() default "Disposable email addresses are not allowed.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

`@interface` khai báo annotation Java. `@Target` giới hạn nơi được gắn là field/tham số. `@Retention(RUNTIME)` giữ metadata để runtime validation đọc được. `@Constraint` nối constraint này với class kiểm tra. `message` là thông báo lỗi; `groups` hỗ trợ chia nhóm validation; `payload` là metadata thêm cho constraint, **không phải JWT payload**.

`EmailDomainValidator implements ConstraintValidator<NonDisposableEmail, String>` nghĩa là validator cho annotation này và kiểu dữ liệu String. Framework gọi `isValid(value, context)`; `true` là qua, `false` tạo violation. Trong Spring Boot, hạ tầng validator tích hợp Spring có thể inject constructor `@Value` cho validator; vì vậy lớp này không cần được dùng như service thủ công chỉ để đọc danh sách cấu hình.

Constructor nhận `Set<String> domains` từ cấu hình, tạo tập domain chữ thường để tra cứu. Lambda `String::toLowerCase` hiện dùng locale mặc định; khi cải thiện xử lý domain có thể dùng `Locale.ROOT` rõ ràng.

### 14.2. Lỗi tách domain hiện tại

Code đang làm:

```java
final int atIndex = email.indexOf("@") + 1;
final int dotIndex = email.lastIndexOf(".");
final String domain = email.substring(atIndex, dotIndex);
return !this.blocked.contains(domain);
```

Với `abc@mailinator.com`, substring cho ra `mailinator`, trong khi cấu hình chứa `mailinator.com`. Hai chuỗi không bằng nhau, nên bộ lọc hiện có thể **bỏ lọt email disposable**. Với domain viết hoa cũng chưa được chuẩn hóa input. Nếu có `@` nhưng không có dấu chấm hợp lệ sau đó, thao tác substring có thể ném lỗi. `@Email` đứng trên cùng field không bảo đảm validator này được bỏ qua.

Ví dụ sửa thuật toán để học, **chưa thay vào source**:

```java
@Override
public boolean isValid(String email, ConstraintValidatorContext context) {
    if (email == null || email.isBlank()) {
        return true; // để @NotBlank xử lý bắt buộc
    }
    int at = email.lastIndexOf('@');
    if (at <= 0 || at == email.length() - 1) {
        return true; // để @Email xử lý cấu trúc
    }
    String domain = email.substring(at + 1)
        .toLowerCase(java.util.Locale.ROOT);
    return !blocked.contains(domain);
}
```

Đoạn này lấy toàn bộ phần sau `@`, không cắt mất `.com`. Nó chỉ so khớp domain chính xác trong danh sách; chưa tự chặn mọi subdomain, kiểm tra MX, chứng minh hộp thư tồn tại hay thay quy trình xác minh email. Nếu muốn chặn subdomain phải quy định rõ và so sánh theo ranh giới domain.

<a id="buoc-15"></a>
## 15. Xử lý exception — không phải mọi lỗi đều đi qua controller advice

File: [BusinessException.java](asymmectric-encryption/src/main/java/com/doph/exception/BusinessException.java), [ErrorCode.java](asymmectric-encryption/src/main/java/com/doph/exception/ErrorCode.java), [ApplicationExceptionHandler.java](asymmectric-encryption/src/main/java/com/doph/handler/ApplicationExceptionHandler.java).

### 15.1. Từ lỗi nghiệp vụ đến HTTP JSON

```text
Service phát hiện sai điều kiện
    → throw new BusinessException(ErrorCode.USER_NOT_FOUND, userId)
    → ngừng luồng thành công, exception đi lên controller/MVC
    → handler chọn @ExceptionHandler tương ứng
    → đọc status, code, message
    → ResponseEntity<ErrorResponse>
```

`BusinessException extends RuntimeException` là unchecked exception. Nó lưu `ErrorCode` và mảng `args`; constructor gọi `super(...)` để đặt message cho lớp exception cha. `Object... args` là varargs, cho phép truyền nhiều đối số; trong method chúng được xử lý như mảng.

`String.format("User not found with id %s", userId)` thay `%s` bằng giá trị ID. `%s` là format placeholder, không phải regex hay placeholder `${...}` của Spring. Khi không truyền args, code giữ nguyên defaultMessage. Cần truyền `ErrorCode` hợp lệ; handler gọi getter của nó trước nhánh kiểm null ở status, nên nhánh status fallback hiện không thật sự bảo vệ trường hợp errorCode null.

### 15.2. Danh mục mã lỗi hiện có

| Enum / code | HTTP theo cấu hình handler | Tình huống |
|---|---|---|
| `USER_NOT_FOUND` | 404 | Tìm user theo ID thất bại |
| `CHANGE_PASSWORD_MISMATCH` | 400 | New password khác confirm |
| `INVALID_CURRENT_PASSWORD` | 400 | Mật khẩu hiện tại không đúng |
| `THE_ACCOUNT_ALREADY_DEACTIVATED` | 400 | Tắt tài khoản đã tắt |
| `THE_ACCOUNT_ALREADY_ACTIVATED` | 400 | Bật tài khoản đã bật |
| `EMAIL_ALREADY_EXISTS` | 400 | Email đã tồn tại |
| `PHONE_ALREADY_EXISTS` | 400 | Phone đã tồn tại |
| `PASSWORD_MISMATCH` | 400 | Password/confirm lúc đăng ký khác nhau |
| `ERR_USER_DISABLED` | 401 | Provider báo `DisabledException` ở luồng MVC |
| `BAB_CREDENTIALS` | 401 | `BadCredentialsException`; tên enum đang viết `BAB` |
| `USERNAME_NOT_FOUND` | 404 | `UsernameNotFoundException` tới handler MVC |
| `INTERNAL_EXCEPTION` | 500 | Nhánh fallback `Exception` |
| `ENTITY_NOT_FOUND` | 404 | Code JSON thực là `ENTITY_NOTFOUND`, khác tên enum |

Một lỗi không nhất thiết tới đúng handler theo tên mong đợi của người đọc. Ví dụ provider mặc định có thể che `UsernameNotFoundException` thành `BadCredentialsException`, nên email không tồn tại khi login thường được xử lý như sai credentials. Các exception trạng thái khác như locked/credentials expired chưa có handler riêng; nếu được ném tới advice sẽ rơi vào fallback 500 hiện tại.

### 15.3. Vai trò của các annotation trong handler

`@RestControllerAdvice` áp dụng xử lý lỗi chung cho controller và ghi body JSON. Mỗi `@ExceptionHandler(SomeException.class)` chọn method xử lý kiểu exception tương ứng; các method cùng tên `handleException` vẫn hợp lệ vì đây là overload theo kiểu tham số. Framework chọn dựa trên mapping exception, không đơn giản chạy từ trên xuống như chuỗi `if`.

`ErrorResponse` có `message`, `code`, `validationErrors`; `ValidationError` có `field`, `code`, `message`. Cả hai dùng builder để tạo body. `@ToString` sinh mô tả object phục vụ log/debug; nó không quyết định cấu trúc JSON. Không có cấu hình loại null riêng trong các DTO này, nên client có thể thấy field null tùy Jackson configuration.

Ví dụ lỗi nghiệp vụ theo body hiện tại:

```json
{
  "message": "Email already exists",
  "code": "EMAIL_ALREADY_EXISTS",
  "validationErrors": null
}
```

Khi `@Valid` request thất bại, `MethodArgumentNotValidException` chứa danh sách lỗi. Handler dựng một `ValidationError` mỗi lỗi và trả 400; `code` trên lỗi field là tên constraint như `NotBlank`, không phải một `ErrorCode` enum của nghiệp vụ.

```json
{
  "message": null,
  "code": null,
  "validationErrors": [
    {
      "field": "email",
      "code": "NotBlank",
      "message": "VALIDATION.AUTHENTICATION.EMAIL.NOT_BLANK"
    }
  ]
}
```

Đây là body minh họa, số lỗi/thứ tự thực tế có thể khác. Code hiện ép **mọi** phần tử của `ex.getAllErrors()` thành `FieldError`. Nếu sau này có lỗi cấp object/cross-field, ép kiểu có thể hỏng; nên phân biệt `FieldError` và object error. JSON không đọc được hoặc vi phạm DB constraint chưa có handler chuyên biệt; fallback hiện có thể trả 500 thay vì mã lỗi rõ hơn.

### 15.4. Phân biệt lỗi MVC và lỗi tầng filter

```mermaid
flowchart LR
    R[HTTP request] --> F[JwtFilter]
    F --> AU[Authorization trong Security chain]
    AU --> D[DispatcherServlet]
    D --> C[Controller / Service]
    C -. exception .-> H[RestControllerAdvice]
    F -. parse JWT lỗi .-> FE[Cần xử lý tại filter / entry point]
    AU -. thiếu danh tính hoặc quyền .-> SH[Security exception handling]
```

`@RestControllerAdvice` dựa vào cơ chế resolver của Spring MVC; nó không tự bọc toàn bộ servlet filter chain. Lỗi token ném trước DispatcherServlet không tự động trở thành `ErrorResponse` của advice. `ExceptionTranslationFilter` của Security cũng chỉ xử lý các exception Security phù hợp ném trong phạm vi chuỗi mà nó bao, không phải mọi `JwtException` ở mọi vị trí. Xem [MVC exception handling](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html).

Khi hoàn thiện API có thể định nghĩa `AuthenticationEntryPoint` cho chưa xác thực (thường 401), `AccessDeniedHandler` cho đã xác thực nhưng thiếu quyền (thường 403), và xử lý parse JWT ở filter để chuyển tới luồng lỗi phù hợp. Nếu tự viết JSON response trong filter phải dừng chain khi đã trả lỗi và tránh ghi response lần nữa. Source hiện chưa cấu hình các handler JSON Security này, nên không hứa mọi request thiếu/hỏng token đều trả 401 cùng một body; cần kiểm thử runtime để biết response hiện tại.

`@ExceptionHandler(Exception.class)` không bắt các kiểu `Error` như `StackOverflowError`. Sửa getter đệ quy là việc cần làm ở code, không thể dựa vào catch Exception để cứu nó. Log message cho client và log stack trace cho người vận hành cũng là hai việc khác nhau: fallback hiện log lỗi đầy đủ ở server và chỉ trả thông báo nội bộ chung.

<a id="buoc-16"></a>
## 16. Cấu hình OpenAPI / Swagger — mô tả không phải enforcement

File: [OpenApiConfig.java](asymmectric-encryption/src/main/java/com/doph/config/OpenApiConfig.java).

| Annotation | Ý nghĩa |
|---|---|
| `@OpenAPIDefinition` | Metadata chung và danh sách server/security yêu cầu trong tài liệu |
| `@Info` | Mô tả, version tài liệu, điều khoản |
| `@Contact` | Thông tin liên hệ tác giả |
| `@License` | Metadata license |
| `@Server` | Base URL để công cụ tài liệu gửi request |
| `@SecurityScheme(name="bearerAuth", type=HTTP, scheme="bearer")` | Khai báo cơ chế Bearer trong OpenAPI |
| `@SecurityRequirement(name="bearerAuth")` | Tham chiếu đúng tên scheme |
| `@Tag` | Nhóm endpoint Authentication/User |
| `@Schema(example=...)` | Ví dụ field request/response |

`bearerFormat="JWT"` gợi ý định dạng token; `in=HEADER` là metadata vị trí, với HTTP bearer công cụ sử dụng Authorization header. `SecurityScheme` không ký JWT, không tạo filter và không cấp quyền. Bảo mật thực tế nằm ở `SecurityConfig`, filter và method policy.

Tài liệu có server `http://localhost:8080` và placeholder `http://prod.url`; placeholder không phải địa chỉ deployment thật. Đường dẫn UI thường dùng `/swagger-ui/index.html`, đặc tả `/v3/api-docs`, tương ứng whitelist trong config. Global security requirement có thể làm các endpoint auth cũng hiện yêu cầu bearer trong tài liệu dù runtime `permitAll`; muốn tài liệu chính xác hơn cần override security cho operation công khai. Source hiện chưa có override đó.

Trong Swagger UI, nhập token theo hướng dẫn trường Authorize của scheme Bearer, tránh tự thêm `Bearer` lần hai nếu UI đã tự tạo header. Các example tên `Do`, `Phat` cần đổi thành giá trị đạt `@Size(min=5)` khi thử đăng ký.

<a id="buoc-17"></a>
## 17. Chạy dự án, thử API và kiểm chứng

### 17.1. Chuẩn bị đúng thư mục và dependency

Các lệnh sau là hướng dẫn chạy local, không phải log xác nhận đã chạy thành công trong lần biên soạn tài liệu này.

```powershell
# Từ thư mục spring-security
Set-Location .\asymmectric-encryption
java -version
.\mvnw.cmd -v
```

POM yêu cầu Java 25. Wrapper đang khai báo bản phân phối Maven `3.10.1` trong `.mvn/wrapper/maven-wrapper.properties`; nó có thể cần tải Maven/dependency qua mạng. Nếu máy đã có Maven phù hợp, có thể dùng `mvn` thay wrapper. Đừng chạy `mvn compile` ở thư mục ngoài chỉ chứa README mới vì `pom.xml` nằm trong module con.

### 17.2. Cấu hình database local

Mở Docker Desktop/engine rồi chạy từ thư mục module:

```powershell
docker compose up -d postgres
docker compose ps
```

Nếu chưa có `.env`, tạo từ `.env.example`; nếu đã có thì kiểm tra giá trị hiện tại, không cần ghi đè. Bộ giá trị khớp Compose của dự án:

```dotenv
DB_URL=localhost
DB_PORT=5432
DB_NAME=spring_app_db
DB_USERNAME=username
DB_PASSWORD=password
```

Compose đọc `.env` cho cơ chế biến của nó, còn Spring đọc nhờ `spring.config.import`; đây là hai cơ chế riêng. Với volume PostgreSQL đã khởi tạo từ trước, thay biến `POSTGRES_DB`/user/password trong Compose không tự viết lại dữ liệu và credentials đang tồn tại.

### 17.3. Kiểm tra khóa và dữ liệu seed trước khi thử API

Kiểm tra có resource `keys/local-only/private_key.pem` và `public_key.pem`, đúng một cặp, header tương ứng PKCS#8/X.509. Cần chuẩn hóa đường dẫn private key có `//` ở `JwtService` nếu môi trường không resolve được. Cần thống nhất seed tìm `ROLE_USER`, bảo đảm không có dữ liệu role trùng tên trước khi kỳ vọng đăng ký luôn tìm được đúng một role.

Tài liệu này chỉ thêm README, không sửa các dòng code hoặc dữ liệu nêu trên. Các bước khắc phục được liệt kê để người học hiểu nguyên nhân nếu chạy vướng lỗi, không phải lời khẳng định chúng đã được áp dụng.

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd spring-boot:run
```

Ứng dụng dùng port mặc định 8080 khi không có cấu hình khác. Sau khi startup thành công có thể mở `http://localhost:8080/swagger-ui/index.html` hoặc thử bằng Postman/PowerShell. Postman CLI đã được cài theo hướng dẫn workspace; dự án hiện chưa có collection API trong source để chạy sẵn bằng CLI.

### 17.4. Đăng ký → đăng nhập → gọi API → refresh

Ví dụ PowerShell sau dùng tên đạt min 5 ký tự và mật khẩu đạt constraint hiện tại. Cần đổi email/phone nếu dữ liệu này đã đăng ký trước.

```powershell
$baseUrl = 'http://localhost:8080'
$registrationBody = @{
    firstName = 'Thanh'
    lastName = 'Nguyen'
    email = 'learner@example.com'
    phoneNumber = '+84987654321'
    password = 'Study@1234'
    confirmPassword = 'Study@1234'
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "$baseUrl/api/v1/auth/register" `
    -ContentType 'application/json' -Body $registrationBody

$loginBody = @{
    email = 'learner@example.com'
    password = 'Study@1234'
} | ConvertTo-Json
$loginResponse = Invoke-RestMethod -Method Post `
    -Uri "$baseUrl/api/v1/auth/login" `
    -ContentType 'application/json' -Body $loginBody

$authHeaders = @{ Authorization = "Bearer $($loginResponse.access_token)" }
Invoke-RestMethod -Method Get -Uri "$baseUrl/api/v1/users/me" `
    -Headers $authHeaders

$updateBody = @{ firstName = 'Minhh'; lastName = 'Nguyen' } | ConvertTo-Json
Invoke-RestMethod -Method Patch -Uri "$baseUrl/api/v1/users/me" `
    -Headers $authHeaders -ContentType 'application/json' -Body $updateBody

$refreshBody = @{ refreshToken = $loginResponse.refresh_token } | ConvertTo-Json
$refreshResponse = Invoke-RestMethod -Method Post `
    -Uri "$baseUrl/api/v1/auth/refresh" `
    -ContentType 'application/json' -Body $refreshBody

# Từ đây dùng access token mới cho request sau
$authHeaders = @{ Authorization = "Bearer $($refreshResponse.access_token)" }
```

PowerShell dùng dấu backtick cuối dòng để nối lệnh; không thêm khoảng trắng sau dấu đó. Response PATCH 204 không có JSON body là bình thường. Nếu dùng Postman, header API bảo vệ là `Authorization: Bearer <access_token>`, đăng ký/login dùng body raw JSON với các tên field trên.

Đổi mật khẩu dùng `POST /api/v1/users/me/password` với JSON:

```json
{
  "currentPassword": "Study@1234",
  "newPassword": "Study@5678",
  "confirmNewPassword": "Study@5678"
}
```

### 17.5. Những ca kiểm tra có ý nghĩa

| Ca thử | Kỳ vọng theo thiết kế / điểm cần quan sát ở code hiện tại |
|---|---|
| Đăng ký hợp lệ lần đầu | 201; DB có user, hash password, liên kết role |
| Đăng ký trùng email/phone | 400 business error khi kiểm tra trước bắt được |
| Tên `Do` hoặc `Phat` | 400 validation do min length 5 |
| Password/confirm khác | 400 `PASSWORD_MISMATCH` |
| Login đúng / sai password | 200 có token / thường 401 `BAB_CREDENTIALS` |
| GET `/me` với access hợp lệ | 200, không trả password hash |
| GET `/me` thiếu hoặc hỏng token | Bị từ chối/lỗi; chưa bảo đảm status/body JSON thống nhất |
| Dùng refresh token gọi `/me` | Lẽ ra cần bị từ chối; code hiện chưa chặn theo loại |
| Refresh bằng access token | Bị từ chối loại, nhưng RuntimeException hiện có thể thành 500 |
| Refresh token hết hạn | Parser có thể ném lỗi; mapping hiện chưa chuyên biệt |
| Deactivate rồi thử token cũ và login mới | So sánh rõ: JWT filter chưa kiểm enabled, provider login có kiểm |
| PATCH `dateOfBirth` | Chưa được mapper cập nhật |
| DELETE `/me` rồi GET lại | Chưa xóa vì service là stub |
| Tạo rồi sửa entity có audit | Kiểm tra DB, không chỉ object trong bộ nhớ |
| Restart nhiều lần | Quan sát lỗi seed lệch `USER_ROLE`/`ROLE_USER` |

### 17.6. Test có sẵn và phạm vi đã kiểm chứng

File [AsymmectricEncryptionApplicationTests.java](asymmectric-encryption/src/test/java/com/doph/AsymmectricEncryptionApplicationTests.java) chỉ có `contextLoads()` với `@SpringBootTest` và `@Test`. `@SpringBootTest` yêu cầu khởi tạo context ứng dụng; `@Test` đánh dấu method JUnit. Test này không chứng minh các luồng token, authorization, CSRF hay error response đều đúng. Context test còn có thể khởi chạy runner và cần database/resource/config phù hợp.

```powershell
.\mvnw.cmd test
```

Trong lần viết README này đã đọc toàn bộ Java source, cấu hình, POM, test và README cũ. Thử `mvn -o -DskipTests compile` trong module **chưa qua giai đoạn resolve POM**: cache Maven chưa có `org.springframework.boot:spring-boot-starter-parent:4.1.2`, offline mode không tải được. Vì vậy chưa xác nhận compile hoặc chạy integration test thành công; không kết luận dependency đó không tồn tại chỉ từ lỗi offline. Những phân tích hành vi trong README là đối chiếu source và tài liệu framework, không phải kết quả chạy đầy đủ các ca API.

<a id="java"></a>
## 18. Các quy tắc Java và design pattern gặp trong dự án

### 18.1. `class`, `interface`, `extends`, `implements`, `impl`

| Cú pháp thực tế | Cách hiểu |
|---|---|
| `Role extends BaseEntity` | Class kế thừa một class cha, nhận hành vi/field được phép |
| `JwtFilter extends OncePerRequestFilter` | Kế thừa lớp nền framework, override điểm mở rộng |
| `UserService extends UserDetailsService` | Interface mở rộng interface khác |
| `UserServiceImpl implements UserService` | Class thực hiện hợp đồng interface |
| `User implements UserDetails` | Entity cung cấp những method Security yêu cầu |
| `UserRepository extends JpaRepository<User,String>` | Interface mở rộng repository generic |
| `BusinessException extends RuntimeException` | Tạo loại unchecked exception riêng |

Class Java chỉ có một class cha trực tiếp, nhưng có thể implements nhiều interface. Interface không được `new` trực tiếp như class cụ thể; Spring có thể cung cấp một implementation hoặc proxy cho nó. Package `impl` và hậu tố `Impl` là quy ước đặt tên để người đọc nhận ra implementation, **không phải từ khóa đặc biệt của Java**.

`UserService service = ...` có thể tham chiếu instance `UserServiceImpl`. Lời gọi `service.changePassword(...)` chạy implementation tương ứng: đó là đa hình. Controller phụ thuộc interface giúp giảm sự gắn chặt với một lớp cụ thể; không có quy tắc bắt buộc mọi class trong Spring đều phải có interface riêng.

### 18.2. Các keyword cần phân biệt

| Keyword | Trong dự án |
|---|---|
| `public` | Cho phép truy cập theo phạm vi public của type/member |
| `private` | Chi tiết nội bộ class; field thường đóng gói bằng getter/setter |
| `protected` | Cho phép kế thừa/truy cập theo quy tắc Java, như `doFilterInternal` |
| Không ghi modifier | Package-private; ví dụ bean `passwordEncoder()` |
| `final` biến/field | Không gán lại reference; object mà nó trỏ tới vẫn có thể thay đổi |
| `static` | Thành viên thuộc class, như utility method và hằng TOKEN_TYPE |
| `void` | Method không trả giá trị dữ liệu |
| `return` | Kết thúc method/lambda hiện tại và có thể trả giá trị |
| `throw` | Ném một exception thực tế |
| `throws` | Khai báo method có thể phát sinh exception |
| `super(...)` | Gọi constructor class cha; BusinessException đặt message cho RuntimeException |
| `this` | Instance hiện tại; `this.password` là field, không phải lời gọi getter |

Ví dụ `final User user = ...` vẫn cho phép `user.setEnabled(false)`; chỉ không được gán `user = anotherUser`. `final` trong Java không đồng nghĩa `@Column(updatable=false)`.

### 18.3. Generic và `Optional`

`Optional<User>` biểu diễn kết quả có/không có User. `orElseThrow(() -> new ...)` chỉ tạo và ném exception khi rỗng. Không nên gọi `.get()` không kiểm tra. `Optional.empty()` khác `null`: bản thân Optional vẫn là một object đại diện trạng thái vắng.

`Collection<? extends GrantedAuthority>` là collection chứa đối tượng của một kiểu con phù hợp với `GrantedAuthority`. Dấu `?` là wildcard của generic, khác ký tự `?` trong regex. `Map<String,Object>` có key String và value có thể nhiều kiểu; JWT claims dùng nó để chứa dữ liệu. `List.of()` tạo list không cho sửa cấu trúc; code dùng nó cho trường hợp rỗng.

### 18.4. Lambda, method reference và stream

```java
role -> new SimpleGrantedAuthority(role.getName())
Role::getName
Authentication::getName
() -> Optional.of("SYSTEM")
args -> { /* nội dung CommandLineRunner */ }
```

Lambda là cách cung cấp implementation cho một functional interface có một abstract method phù hợp. Với `CommandLineRunner`, lambda cung cấp thân `run`; với `AuditorAware`, lambda cung cấp thân `getCurrentAuditor`.

`Role::getName` trong stream tương đương `role -> role.getName()`. `stream().map(...).toList()` đi qua từng phần tử, biến đổi và thu kết quả. Nó không tự chạy SQL hoặc sửa dữ liệu role. Nếu collection là quan hệ lazy, việc truy cập có thể kích hoạt nạp dữ liệu bởi ORM, một cơ chế riêng.

### 18.5. Lombok và Java annotation

Annotation là metadata để compiler, processor hoặc runtime framework xử lý; dấu `@` không có một hành vi chung duy nhất.

| Annotation Lombok | Mã mà nó giúp sinh |
|---|---|
| `@Getter`, `@Setter` | Getter/setter cho field theo quy tắc Lombok |
| `@NoArgsConstructor` | Constructor không tham số; hữu ích cho JPA/Jackson |
| `@AllArgsConstructor` | Constructor nhận các field thuộc lớp theo cấu hình |
| `@RequiredArgsConstructor` | Constructor cho field final chưa gán và field yêu cầu thích hợp |
| `@Builder` | Builder dạng `.builder().field(value).build()` |
| `@SuperBuilder` | Builder làm việc với hệ kế thừa được cấu hình tương ứng |
| `@Slf4j` | Field logger `log` |
| `@ToString` | Method `toString()` phục vụ biểu diễn object |
| `@NonNull` | Đánh dấu/ràng buộc null theo cơ chế Lombok và vị trí sử dụng |

`@Override` là annotation Java để compiler kiểm tra method đang override/implement đúng hợp đồng. Nó không phải annotation của Spring. `import lombok.*` chỉ import kiểu trực tiếp trong package đó, không import mọi package con. Khi version thư viện thay đổi vị trí annotation, cần kiểm tra API thực tế và dependency tree.

### 18.6. Các pattern/kiến trúc có thể nhận ra

| Mẫu | Biểu hiện cụ thể | Giá trị |
|---|---|---|
| Phân lớp | Controller → Service → Repository | Tách giao thức HTTP, nghiệp vụ và persistence |
| DTO + Mapper | Request/Response và `UserMapper` | Kiểm soát dữ liệu qua API, bảo vệ field nội bộ |
| Repository | `JpaRepository` và proxy do Spring tạo | Đóng gói truy cập dữ liệu |
| Dependency Injection | Inject service/encoder/repository qua constructor | Cấp dependency và giảm tự khởi tạo |
| Strategy qua interface | `PasswordEncoder` nhận implementation BCrypt | Chọn thuật toán qua hợp đồng chung |
| Builder | Lombok builder, `Jwts.builder`, `HttpSecurity` | Xây object/cấu hình theo bước |
| Chuỗi filter | Mỗi filter xử lý rồi chuyển tiếp | Chia trách nhiệm xử lý request |
| Template Method | `OncePerRequestFilter` gọi điểm mở rộng `doFilterInternal` | Tái sử dụng quy trình nền |
| Proxy / AOP | Transaction và method security của Spring | Bao thêm xử lý quanh method |
| Listener/callback | `AuditingEntityListener` nhận lifecycle callback | Tự gán audit theo sự kiện JPA |
| Xử lý lỗi tập trung | `ApplicationExceptionHandler` | Đồng nhất phần lớn lỗi ở MVC |

Đây là cách nhận diện vai trò để đọc code, không phải tuyên bố dự án triển khai đầy đủ mọi biến thể pattern. Đặc biệt chữ **pattern** có ba nghĩa cần phân biệt: regex trong validation, mẫu đường dẫn trong Security, và design pattern như Builder/Strategy.

<a id="jpa"></a>
## 19. JPA mở rộng — lifecycle, kế thừa và quan hệ 1–1, 1–n, n–1

Các quan hệ và class minh họa ở phần này **chưa tồn tại trong dự án**. Các đoạn là fragment để chỉ ra mapping; không phải file Java hoàn chỉnh để chép đè `User`. Khi triển khai cần thêm import, constructor, getter/setter, repository/DTO và chính sách nghiệp vụ thích hợp.

### 19.1. JPA, Hibernate, Spring Data JPA, JDBC khác nhau thế nào?

```text
UserRepository (Spring Data JPA proxy)
    → EntityManager (API JPA/Jakarta Persistence)
    → Hibernate (provider ORM)
    → JDBC driver PostgreSQL
    → PostgreSQL
```

**JPA/Jakarta Persistence** là chuẩn API và mapping cho persistence Java; **Hibernate** là implementation ORM thường được starter sử dụng; **Spring Data JPA** cung cấp abstraction repository để giảm thao tác EntityManager thủ công; **JDBC** là API/driver giao tiếp SQL với database. Có thể dùng JPA mà không dùng Spring Data repository, và thêm JPA không có nghĩa mọi truy vấn đều không cần hiểu SQL.

ORM ánh xạ field/quan hệ object sang bảng/cột/khóa ngoại. `User` là entity, `RegistrationRequest` là DTO. Quan hệ kế thừa `Role extends BaseEntity` trong Java khác quan hệ khóa ngoại `User` có nhiều `Role`; không thể thay `@ManyToMany` bằng `extends` để diễn đạt cùng ý nghĩa.

### 19.2. Bốn trạng thái entity và persistence context

| Trạng thái | Diễn giải | Ví dụ |
|---|---|---|
| New / transient | Object mới, chưa được quản lý như entity persistent | `User.builder()...build()` trước persist |
| Managed | Đang thuộc persistence context | User được load trong một transaction/context đang hoạt động |
| Detached | Từng persistent nhưng không còn được context hiện tại quản lý | Object sau khi context đóng hoặc được detach |
| Removed | Đã đánh dấu xóa trong context, chờ đồng bộ SQL | Sau `EntityManager.remove` hợp lệ |

Persistence context theo dõi danh tính và trạng thái entity. Với một entity managed, đổi field trong transaction có thể được Hibernate phát hiện qua **dirty checking** và ghi khi flush, không nhất thiết phải gọi save lần nữa. Với detached entity, đổi object Java không tự cập nhật database.

`save` của Spring Data JPA chọn `persist` cho entity mới hoặc `merge` cho entity được nhận diện đã tồn tại. `merge` sao chép trạng thái vào instance managed và trả nó; instance truyền vào không vì thế tự trở thành managed. Vì vậy nếu cần dùng kết quả sau merge, nên dùng giá trị `save` trả về thay vì mặc định hai reference luôn cùng instance. Quy tắc phát hiện mới còn xét version/ID và có thể tùy biến. Xem [Persisting Entities](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html).

Trong code `register`, service transaction giúp user và role được xử lý trong cùng đơn vị công việc. Với các service method chưa có transaction, thời gian sống context còn phụ thuộc cấu hình, kể cả cơ chế mở EntityManager cho web request; không nên dựa vào đó thay cho việc thiết kế transaction ở tầng nghiệp vụ.

### 19.3. Flush, commit, rollback và cập nhật đồng thời

`flush` đồng bộ các thay đổi đang theo dõi thành SQL, nhưng không đồng nghĩa transaction đã commit. `commit` xác nhận transaction thành công; rollback hủy các thay đổi transactional chưa commit. `saveAndFlush` có thể đẩy SQL sớm nhưng không tự thay thế ranh giới transaction nghiệp vụ.

Nếu hai request cùng đọc user rồi ghi, request sau có thể ghi đè dữ liệu request trước khi không có cơ chế phát hiện. Ví dụ thêm field:

```java
@Version
private Long version;
```

`@Version` hỗ trợ optimistic locking: provider kiểm tra phiên bản khi update/delete và báo lỗi khi entity đã bị thay đổi không tương thích. Đây không phải trường audit và không thay thế `@LastModifiedDate`. Dự án hiện chưa khai báo `@Version`; khi thêm cần thiết kế response cho xung đột. Xem [JPA Version](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/Version.html).

### 19.4. Một–một: user có một hồ sơ mở rộng

Tình huống: tách phần hồ sơ dài/ít dùng sang `UserProfile`, mỗi profile thuộc đúng một user và một user có tối đa một profile.

```text
USERS(id)  1 ───── 0..1 USER_PROFILES(id, user_id UNIQUE NOT NULL)
```

Class minh họa mới, đặt foreign key ở profile:

```java
@Entity
@Table(name = "USER_PROFILES")
public class UserProfile extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false, unique = true)
    private User user;

    private String bio;
}
```

Field bổ sung minh họa ở `User` nếu cần truy cập ngược:

```java
@OneToOne(mappedBy = "user", fetch = FetchType.LAZY)
private UserProfile profile;
```

`UserProfile.user` sở hữu quan hệ vì giữ FK. `unique=true` ngăn hai profile cùng trỏ một user ở database; `nullable=false` không cho profile thiếu user. Điều này không tự buộc **mọi user phải có profile**: user có thể chưa có dòng profile. `optional=false` mô tả association bắt buộc từ phía profile trong mô hình JPA. `mappedBy="user"` tham chiếu field trong profile.

Nếu chỉ cần đi profile → user, có thể bỏ field ngược trên User và dùng quan hệ một chiều. `LAZY` với quan hệ to-one là gợi ý; khả năng trì hoãn thực tế phụ thuộc provider, bytecode enhancement và mapping. Không dùng nó như bảo đảm tuyệt đối không query khi load user. Xem [OneToOne](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/OneToOne.html).

### 19.5. Một–nhiều và nhiều–một: user có nhiều địa chỉ

Đây là **hai góc nhìn của cùng một quan hệ**: một user có nhiều address; mỗi address thuộc một user. Không cần một bảng nối riêng nếu FK đặt trong bảng address.

```text
USERS(id)  1 ───── n USER_ADDRESSES(id, user_id FK, line1)
```

Class mới minh họa phía nhiều:

```java
@Entity
@Table(name = "USER_ADDRESSES")
public class UserAddress extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    private String line1;
}
```

Field minh họa thêm vào `User`, nếu địa chỉ hoàn toàn phụ thuộc vòng đời user:

```java
@OneToMany(
    mappedBy = "user",
    cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE},
    orphanRemoval = true
)
private List<UserAddress> addresses = new ArrayList<>();
```

`UserAddress.user` là owning side chứa FK, không có `mappedBy`. `User.addresses` là inverse side, tham chiếu field `user` của address. Khi thêm địa chỉ, cần đặt cả `address.setUser(user)` và thêm vào collection nếu đang quản lý hai phía. Chỉ `user.getAddresses().add(address)` mà quên đặt owner có thể không ghi FK đúng. Khi dùng builder, cần xem xét giá trị mặc định của collection, chẳng hạn cơ chế builder default; field initializer không luôn được builder sử dụng như mong muốn.

`orphanRemoval=true` cho phép xóa địa chỉ bị loại khỏi quan hệ với chủ sở hữu theo lifecycle JPA; nó phù hợp khi địa chỉ là dữ liệu con không tồn tại độc lập. `CascadeType.REMOVE` truyền thao tác xóa parent sang con. Đây là quyết định nghiệp vụ của **ví dụ**, không phải cấu hình hiện tại của User và không tự nên áp dụng cho mọi quan hệ. Không cascade remove từ address sang user, vì xóa một địa chỉ không được làm mất chủ tài khoản. Xem [ManyToOne](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/ManyToOne.html) và [OneToMany](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/OneToMany.html).

### 19.6. Khi nào không nên dùng cascade xóa / orphan removal?

Đơn hàng, giao dịch hay audit nghiệp vụ có thể phải giữ lại sau khi user bị vô hiệu hóa. Quan hệ user → order vẫn là 1–n, nhưng chưa chắc được xóa cascade như address. Có thể cần soft delete, ẩn danh hóa hoặc từ chối xóa khi còn tham chiếu, tùy yêu cầu hệ thống.

Ở quan hệ nhiều–nhiều `User`–`Role`, xóa user thường chỉ nên gỡ liên kết của user đó; role dùng chung còn cần cho người khác. Tránh thêm `CascadeType.ALL` tùy tiện vì ALL bao gồm REMOVE. `PERSIST`, `MERGE`, `REMOVE` của JPA khác `ON DELETE CASCADE` ở database và khác lựa chọn EAGER/LAZY khi đọc.

### 19.7. Nếu bảng nối cần thêm thông tin: tách entity liên kết

Hiện `USERS_ROLES` chỉ được dùng làm bảng nối. Nếu muốn lưu `assignedAt`, `assignedBy`, `expiresAt`, trạng thái hoặc phạm vi role, nên mô hình hóa **`UserRole` thành entity**. Ví dụ mapping rút gọn:

```java
@Entity
@Table(
    name = "USER_ROLE_ASSIGNMENTS",
    uniqueConstraints = @UniqueConstraint(columnNames = {"USER_ID", "ROLE_ID"})
)
public class UserRole extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ROLE_ID", nullable = false)
    private Role role;

    private LocalDateTime expiresAt;
}
```

Lúc đó một User có nhiều UserRole, một Role có nhiều UserRole; mỗi UserRole trỏ một User và một Role. `BaseEntity.createdDate/createdBy` có thể dùng để biết lúc/ai gán. Constraint cặp ngăn gán trùng nếu nghiệp vụ chỉ cho một assignment mỗi cặp; nếu muốn giữ lịch sử nhiều đợt gán thì phải thiết kế khóa/constraint khác. Cần thay mapping many-to-many cũ theo mô hình mới, không tạo thêm bảng rồi coi hai mô hình tự đồng bộ.

### 19.8. Mặc định fetch và bài toán N+1

| Mapping | Mặc định JPA | Trong dự án / ví dụ |
|---|---|---|
| `@OneToOne` | EAGER | Ví dụ yêu cầu LAZY |
| `@ManyToOne` | EAGER | Ví dụ yêu cầu LAZY |
| `@OneToMany` | LAZY | Ví dụ dùng mặc định |
| `@ManyToMany` | LAZY | `User.roles` ghi đè EAGER, `Role.users` dùng mặc định |

EAGER yêu cầu dữ liệu liên quan được nạp sớm; LAZY là gợi ý trì hoãn. LAZY không có nghĩa cột quan hệ bị loại khỏi database. Truy cập lazy association sau khi context không còn có thể gây lỗi như `LazyInitializationException` trong Hibernate. Cách xử lý thường là tải đúng phần cần trong transaction rồi map DTO, không bật EAGER cho mọi quan hệ.

N+1 xảy ra khi query danh sách N entity rồi mỗi entity lại phát sinh query đọc quan hệ. Tùy trường hợp có thể dùng fetch join, `@EntityGraph`, projection hoặc batch fetching, rồi đo SQL thực tế. `User.roles` EAGER không tự loại trừ N+1. Fetch join nhiều collection hoặc phân trang cũng cần kiểm tra query/result, không sao chép một quy tắc cho mọi endpoint.

### 19.9. Ba kiểu kế thừa cần tách bạch

1. **Java inheritance:** `Role extends BaseEntity` tái sử dụng field/method.
2. **`@MappedSuperclass`:** mapping field cha được đưa vào bảng entity con; đó là cách BaseEntity hiện dùng.
3. **Entity inheritance bằng `@Inheritance`:** có các chiến lược như SINGLE_TABLE, JOINED, TABLE_PER_CLASS để lưu cả một hệ entity kế thừa; dự án chưa dùng.

Không vì `Role` kế thừa BaseEntity mà database tự tạo quan hệ 1–1 giữa bảng role và base. Cũng không vì `User` implements UserDetails mà JPA tạo bảng UserDetails. Interface Java, quan hệ object và schema database là các lớp khái niệm khác nhau.

<a id="phan-quyen"></a>
## 20. Phân quyền mở rộng — role, authority và chủ sở hữu

Các policy trong phần này là **ví dụ chưa áp dụng**. Source hiện chỉ có public URL và `authenticated()`, mặc dù đã có bảng role và bật method security.

### 20.1. Authentication khác authorization

Authentication xác định **bạn là ai** bằng password/token. Authorization xác định **bạn được làm gì** sau khi biết danh tính. JWT hợp lệ không tự cho phép xóa người khác, và có một role trong database chưa có ý nghĩa bảo vệ endpoint nếu code không kiểm tra role đó.

Role thường là nhóm chức năng như `ROLE_USER`, `ROLE_ADMIN`; authority có thể là quyền cụ thể như `user:read`, `user:deactivate`. Trong Security, cả hai cuối cùng thường xuất hiện dưới dạng `GrantedAuthority` chứa chuỗi.

| Biểu thức với cấu hình prefix mặc định | Chuỗi authority cần có |
|---|---|
| `hasRole("USER")` | `ROLE_USER` |
| `hasRole("ADMIN")` | `ROLE_ADMIN` |
| `hasAuthority("ROLE_ADMIN")` | Chính xác `ROLE_ADMIN` |
| `hasAuthority("user:read")` | Chính xác `user:read` |

Không truyền `ROLE_ADMIN` vào `hasRole` theo cách dùng prefix mặc định. Không có quy tắc tự động “ADMIN được mọi quyền USER” nếu chưa cấp cả role hoặc cấu hình role hierarchy.

### 20.2. Ví dụ kiểm quyền tại URL

Fragment thay thế phần `authorizeHttpRequests` nếu triển khai thêm các endpoint admin:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(PUBLIC_URLS).permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
    .requestMatchers("/api/v1/users/me", "/api/v1/users/me/**")
        .authenticated()
    .anyRequest().authenticated()
)
```

Danh sách role của user phải thực sự có `ROLE_ADMIN`; thêm rule không tự tạo role, không tự cấp nó và không tự tạo controller `/admin/**`. Vì filter hiện tải user/roles từ DB mỗi request, việc đổi role có thể được phản ánh ở request sau khi transaction cập nhật đã commit. Quyền trong JWT không cần đổi ở thiết kế hiện tại vì JWT chưa chứa quyền.

Có thể phân biệt HTTP method, ví dụ GET cần read, DELETE cần delete, bằng matcher có `HttpMethod`. Đặt rule cụ thể trước `anyRequest`. Xem [request authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html).

### 20.3. Ví dụ quyền tại method

Vì đã bật `@EnableMethodSecurity`, có thể gắn policy vào service bean. Ví dụ **gắn lên method hiện có** nếu muốn deactivate luôn yêu cầu role USER:

```java
@PreAuthorize("hasRole('USER')")
@Override
public void deactivateAccount(String userId) {
    // Nội dung nghiệp vụ hiện có; policy chạy trước khi vào method.
}
```

Đây là fragment giải thích vị trí annotation, không phải implementation thay thế chứa comment. Import annotation từ `org.springframework.security.access.prepost.PreAuthorize`. `@PreAuthorize` dùng biểu thức SpEL, không phải regex Java hay SQL.

Policy chỉ yêu cầu USER chưa đủ nếu method có thể nhận ID của bất cứ user nào. Ví dụ policy cho **chính chủ hoặc ADMIN**, gắn lên một method có tham số đầu tiên là user ID:

```java
@PreAuthorize("hasRole('ADMIN') or #p0 == authentication.principal.id")
```

`#p0` là tham số thứ nhất, tránh phụ thuộc việc tên tham số được giữ trong bytecode. `authentication.principal.id` đọc ID từ principal `User` của dự án. Nếu đổi kiểu principal thì phải đổi policy. Với endpoint `/me`, controller hiện đã lấy ID từ principal nên không nhận một ID tùy ý từ client; với endpoint `/users/{id}` mới, cần kiểm tra ownership rõ ràng ở service hoặc policy.

`@PreAuthorize` chạy thông qua proxy của Spring. Tự gọi method được bảo vệ từ method khác trong cùng instance không luôn đi qua kiểm tra đó. Cần đặt ranh giới service và cách gọi cho policy thực sự có hiệu lực. Xem [method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html).

### 20.4. Nếu cần quyền chi tiết hơn role

Có thể thêm `Permission` và quan hệ Role–Permission, rồi mở rộng `getAuthorities()` để trả cả `ROLE_USER` lẫn quyền như `user:read`. Sau đó dùng `hasAuthority('user:read')`. Hiện dự án chỉ trả `Role.name`, nên viết policy permission mà không thêm mapping chuyển đổi sẽ không tự có authority đó.

Ví dụ ma trận nghiệp vụ để thiết kế trước khi viết policy:

| Hành động | USER | ADMIN |
|---|---|---|
| Xem/sửa hồ sơ mình | Có | Có |
| Đổi password của mình | Có, kiểm mật khẩu hiện tại | Có, kiểm mật khẩu hiện tại |
| Xem danh sách user | Chưa cho | Có nếu có endpoint/policy |
| Tắt tài khoản khác | Không | Có theo quy trình admin |
| Tự cấp role ADMIN qua đăng ký | Không | Không cho qua request đăng ký public |

Đây là ví dụ quyết định nghiệp vụ, không phải quyền sẵn có trong source. RegistrationRequest hiện không có field role và service luôn gán ROLE_USER; giữ quyết định cấp quyền ở server. Đừng mở role admin chỉ bằng cách thêm field `roles` vào JSON đăng ký.

### 20.5. Khi bổ sung phân quyền cần đi cùng kiểm trạng thái tài khoản

Role check không sửa được lỗi JWT filter bỏ qua enabled/locked hoặc chấp nhận refresh token làm access. Hoàn thiện authentication trước, sau đó kiểm quyền và mapping lỗi. Phân biệt 401 thiếu/chưa có authentication hợp lệ với 403 đã được nhận diện nhưng không đủ quyền; code hiện chưa có JSON Security error handler thống nhất để bảo đảm phân biệt này ở mọi trường hợp.

<a id="doi-chieu"></a>
## 21. Đối chiếu README cũ và giới hạn mã nguồn hiện tại

### 21.1. Những điểm tài liệu mới đã cập nhật theo code

| Nội dung cũ/dễ đọc nhầm | Mô tả đúng theo source hiện tại |
|---|---|
| `getPassword()` tự gọi lại chính nó | Getter đã trả trực tiếp `this.password` |
| `Role` khai báo ID thêm dù kế thừa BaseEntity | ID riêng trong Role đã được comment; dùng ID kế thừa |
| `findByEmailIgnoreCAse` | Tên đúng hiện tại là `findByEmailIgnoreCase` |
| Public key dùng PKCS#8 | Hiện dùng `X509EncodedKeySpec`; private dùng PKCS#8 |
| Đọc resource theo package bằng Class API | Hiện dùng `getClassLoader().getResourceAsStream` |
| Auditor luôn lấy user ID từ `ApplicationAuditorAware` | Bean hiện dùng lambda lấy email hoặc SYSTEM; class kia chưa được đăng ký |
| `User` cũng có toàn bộ field BaseEntity | User chưa extends BaseEntity, chỉ có audit thời gian |
| Trả entity User trực tiếp cho GET `/me` | Hiện có `UserResponse` và mapper, không trả password hash |
| README ghi các tên như RegisterRequest/RefreshTokenRequest | Class thực tế là `RegistrationRequest`/`RefreshRequest` |
| Không thấy import `.env` trong cấu hình đầu bài cũ | Source đã có `optional:file:.env[.properties]` |
| Các bản handler rải rác trong README cũ | Tài liệu này bám `ApplicationExceptionHandler` hiện tại, nêu rõ fallback và giới hạn |

### 21.2. Những việc source vẫn chưa hoàn thiện

Đây là danh sách để hiểu và có thể sửa ở một lần làm việc riêng; lần này không thay đổi Java, cấu hình hay database.

| Vị trí | Điểm còn tồn tại | Tham chiếu phần giải thích |
|---|---|---|
| Runner | Tìm USER_ROLE nhưng lưu ROLE_USER, có thể tạo trùng | 1.3 |
| `Role.name` | Chưa có constraint unique theo tên | 1.3, 5.2 |
| `JwtService` | Đường dẫn private key có double slash | 7.5 |
| `JwtFilter` | Kiểm tra tiền tố Bearer chưa đủ chặt | 8.3 |
| `JwtFilter`/`isTokenValid` | Chưa giới hạn token loại ACCESS | 7.6, 8.3 |
| `JwtFilter` | Chưa kiểm tra trạng thái enabled/locked/credentials | 8.3 |
| Filter error handling | JWT exception chưa được đổi thành JSON lỗi Security thống nhất | 15.4 |
| Refresh | Chưa kiểm trạng thái user, chưa rotation/revocation | 10.4 |
| Password change/deactivate | Chưa có cơ chế thu hồi token đã cấp | 7.7, 8.3 |
| `UserMapper` | Chưa cập nhật `dateOfBirth` | 5.4 |
| DTO update/password/refresh | Thiếu constraint; refresh thiếu `@Valid` | 9, 13 |
| `EmailDomainValidator` | Cắt mất đuôi domain, chưa chuẩn hóa input, có thể substring lỗi | 14.2 |
| Exception handler | Ép toàn bộ object error thành FieldError; một số lỗi rơi 500 | 15.3 |
| `ErrorCode` | Tên BAB_CREDENTIALS và code ENTITY_NOTFOUND chưa nhất quán | 15.2 |
| `deleteAccount` | Chưa có implementation, job hay cơ chế xóa thật | 5.5, 6 |
| Verify email/phone | Mới có cờ, chưa có quy trình xác minh | 4.2 |
| Authorization | Có role nhưng chưa có rule role/permission cụ thể | 20 |
| Khóa demo | Private key đang được theo dõi trong Git | 7.2 |
| Tests | Chỉ có contextLoads, chưa test nghiệp vụ/security | 17.6 |

Nếu sửa tiếp, nên ưu tiên: ổn định startup/seed và đường dẫn khóa; kiểm loại token, trạng thái tài khoản và lỗi Security; sửa validation/mapper; sau đó hoàn thiện phân quyền, quản lý vòng đời token và các tính năng còn stub. Không nên mô tả một biện pháp trong README như thể đã được triển khai nếu chưa sửa và kiểm chứng code.

<a id="tra-cuu"></a>
## 22. Tra cứu tất cả class, interface và annotation

### 22.1. Toàn bộ Java source chính

| File | Vai trò và phần đọc |
|---|---|
| [AsymmectricEncryptionApplication](asymmectric-encryption/src/main/java/com/doph/AsymmectricEncryptionApplication.java) | Main, component scan, CommandLineRunner seed role — 1 |
| [SecurityConfig](asymmectric-encryption/src/main/java/com/doph/security/SecurityConfig.java) | SecurityFilterChain, URL rule, stateless, CSRF — 3 |
| [User](asymmectric-encryption/src/main/java/com/doph/user/User.java) | Entity và UserDetails, authorities — 4 |
| [BaseEntity](asymmectric-encryption/src/main/java/com/doph/common/BaseEntity.java) | ID và audit kế thừa — 4, 12 |
| [Role](asymmectric-encryption/src/main/java/com/doph/role/Role.java) | Vai trò và inverse many-to-many — 4 |
| [UserService](asymmectric-encryption/src/main/java/com/doph/user/UserService.java) | Hợp đồng nghiệp vụ và UserDetailsService — 5 |
| [UserServiceImpl](asymmectric-encryption/src/main/java/com/doph/user/impl/UserServiceImpl.java) | Profile/password/status, loadUserByUsername, stub delete — 5 |
| [UserRepository](asymmectric-encryption/src/main/java/com/doph/user/UserRepository.java) | Derived queries cho user — 5 |
| [RoleRepository](asymmectric-encryption/src/main/java/com/doph/role/RoleRepository.java) | Tìm role theo tên — 5, 10 |
| [UserMapper](asymmectric-encryption/src/main/java/com/doph/user/UserMapper.java) | Request → entity, merge profile, entity → response — 5 |
| [BeanConfig](asymmectric-encryption/src/main/java/com/doph/config/BeanConfig.java) | PasswordEncoder, manager từ AuthenticationConfiguration, auditor — 5, 10, 12 |
| [UserController](asymmectric-encryption/src/main/java/com/doph/user/UserController.java) | API `/users/me`, lấy principal — 6 |
| [JwtService](asymmectric-encryption/src/main/java/com/doph/security/JwtService.java) | Ký/parse/kiểm tra/làm mới JWT — 7 |
| [KeyUtils](asymmectric-encryption/src/main/java/com/doph/security/KeyUtils.java) | Đọc resource PEM, chuyển RSA key spec — 7 |
| [JwtFilter](asymmectric-encryption/src/main/java/com/doph/security/JwtFilter.java) | Bearer → UserDetails → SecurityContext — 8 |
| [AuthenticationRequest](asymmectric-encryption/src/main/java/com/doph/auth/request/AuthenticationRequest.java) | DTO login — 9, 13 |
| [RegistrationRequest](asymmectric-encryption/src/main/java/com/doph/auth/request/RegistrationRequest.java) | DTO đăng ký và constraint — 9, 13 |
| [RefreshRequest](asymmectric-encryption/src/main/java/com/doph/auth/request/RefreshRequest.java) | DTO refreshToken camelCase — 9 |
| [AuthenticationResponse](asymmectric-encryption/src/main/java/com/doph/auth/response/AuthenticationResponse.java) | Response token snake_case — 9 |
| [ProfileUpdateRequest](asymmectric-encryption/src/main/java/com/doph/user/request/ProfileUpdateRequest.java) | DTO sửa hồ sơ — 5, 9 |
| [ChangePasswordRequest](asymmectric-encryption/src/main/java/com/doph/user/request/ChangePasswordRequest.java) | DTO đổi password — 5, 9 |
| [UserResponse](asymmectric-encryption/src/main/java/com/doph/user/response/UserResponse.java) | DTO hồ sơ không chứa hash — 6, 9 |
| [AuthenticationService](asymmectric-encryption/src/main/java/com/doph/auth/AuthenticationService.java) | Hợp đồng login/register/refresh — 10 |
| [AuthenticationServiceImpl](asymmectric-encryption/src/main/java/com/doph/auth/impl/AuthenticationServiceImpl.java) | Triển khai xác thực, đăng ký transactional — 10 |
| [AuthController](asymmectric-encryption/src/main/java/com/doph/auth/AuthController.java) | HTTP login/register/refresh — 11 |
| [ApplicationAuditorAware](asymmectric-encryption/src/main/java/com/doph/config/ApplicationAuditorAware.java) | Implementation auditor theo UUID, hiện chưa được dùng — 12 |
| [JpaConfig](asymmectric-encryption/src/main/java/com/doph/config/JpaConfig.java) | Bật auditing, chọn auditor bean — 12 |
| [NonDisposableEmail](asymmectric-encryption/src/main/java/com/doph/validation/NonDisposableEmail.java) | Annotation constraint tự tạo — 14 |
| [EmailDomainValidator](asymmectric-encryption/src/main/java/com/doph/validation/EmailDomainValidator.java) | Thuật toán domain blocklist — 14 |
| [BusinessException](asymmectric-encryption/src/main/java/com/doph/exception/BusinessException.java) | Exception nghiệp vụ kèm mã và args — 15 |
| [ErrorCode](asymmectric-encryption/src/main/java/com/doph/exception/ErrorCode.java) | Enum code/message/status — 15 |
| [ApplicationExceptionHandler](asymmectric-encryption/src/main/java/com/doph/handler/ApplicationExceptionHandler.java) | MVC exception → HTTP JSON — 15 |
| [ErrorResponse](asymmectric-encryption/src/main/java/com/doph/handler/ErrorResponse.java) | Body lỗi chung — 15 |
| [ValidationError](asymmectric-encryption/src/main/java/com/doph/handler/ValidationError.java) | Chi tiết lỗi field — 15 |
| [OpenApiConfig](asymmectric-encryption/src/main/java/com/doph/config/OpenApiConfig.java) | Metadata tài liệu và scheme Bearer — 16 |

### 22.2. Các kiểu framework quan trọng không nằm trong source riêng

| Kiểu | Đóng vai trò gì trong dự án? |
|---|---|
| `Authentication`, `Principal` | Danh tính và thông tin xác thực, dùng tại controller/context |
| `UserDetails`, `UserDetailsService` | Hợp đồng dữ liệu user và dịch vụ tải user |
| `AuthenticationManager`, `AuthenticationConfiguration` | Thực hiện xác thực và cung cấp manager cấu hình bởi Spring |
| `AuthenticationProvider`, `DaoAuthenticationProvider` | Cơ chế provider dùng để lý giải login username/password; không có custom provider class trong repo |
| `UsernamePasswordAuthenticationToken` | Object yêu cầu xác thực ở login hoặc authentication đã chấp nhận trong filter |
| `GrantedAuthority`, `SimpleGrantedAuthority` | Hợp đồng và implementation biểu diễn quyền bằng chuỗi |
| `SecurityContextHolder`, `SecurityContext` | Nơi truy cập/lưu authentication cho luồng xử lý |
| `AnonymousAuthenticationToken` | Biểu diễn anonymous; auditor hiện loại nó |
| `SecurityFilterChain`, `HttpSecurity` | Chain bảo mật và builder tạo chain |
| `OncePerRequestFilter`, `FilterChain` | Lớp nền filter và chuỗi chuyển tiếp |
| `HttpServletRequest`, `HttpServletResponse`, `HttpHeaders` | Đọc request/header và điều khiển response |
| `WebAuthenticationDetailsSource` | Tạo chi tiết request gắn vào authentication |
| `UsernamePasswordAuthenticationFilter` | Mốc thứ tự khi chèn JwtFilter; không phải controller login của dự án |
| `PasswordEncoder`, `BCryptPasswordEncoder` | Hợp đồng băm/kiểm password và implementation BCrypt |
| `JpaRepository`, `EntityManager` | Repository abstraction và API persistence nền |
| `AuditorAware`, `AuditingEntityListener` | Cung cấp người thao tác và gán audit theo lifecycle |
| `CommandLineRunner` | Callback startup để seed role |
| `Jwts`, `Claims`, `JwtException` | Builder/parser, dữ liệu claims và nhóm lỗi JWT |
| `PrivateKey`, `PublicKey`, `KeyFactory` | Đối tượng khóa mật mã và factory đọc key spec |
| `PKCS8EncodedKeySpec`, `X509EncodedKeySpec`, `Base64` | Cách giải mã và diễn giải nội dung PEM |
| `ResponseEntity`, `HttpStatus` | Chọn status, body của HTTP response |
| `ConstraintValidator`, `ConstraintValidatorContext`, `Payload` | Hợp đồng custom validation và metadata |
| `MethodArgumentNotValidException`, `FieldError` | Kết quả validation request MVC thất bại |
| `Optional`, `List`, `Set`, `Map`, `Collection` | Biểu diễn dữ liệu và kết quả có thể vắng |
| `StringUtils`, `CollectionUtils` | Kiểm tra chuỗi không trắng, collection null/rỗng |

Một số import thừa như `StringUtil`, `WebAuthenticationDetails`, `SecurityContext`, `NotNull`, `EmailValidator` nội bộ Hibernate hoặc `RestController` trong handler không tự kích hoạt cơ chế nào. Import chỉ giúp tham chiếu tên kiểu; phải thực sự sử dụng annotation/method/type ở vị trí có ý nghĩa. Khi dọn code có thể bỏ import không dùng, nhưng không coi chúng là tính năng đã triển khai.

### 22.3. Chỉ mục annotation xuất hiện trong code

| Nhóm | Annotation | Phần giải thích |
|---|---|---|
| Boot / bean | `@SpringBootApplication`, `@Configuration`, `@Bean`, `@Service`, `@Component` | 1, 3, 5 |
| Security | `@EnableWebSecurity`, `@EnableMethodSecurity` | 3 |
| Web MVC | `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PatchMapping`, `@DeleteMapping`, `@RequestBody`, `@ResponseStatus` | 6, 11 |
| Entity | `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@MappedSuperclass` | 4 |
| Quan hệ hiện tại | `@ManyToMany`, `@JoinTable`, `@JoinColumn` | 4.5 |
| Auditing | `@EntityListeners`, `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy`, `@EnableJpaAuditing` | 12 |
| Transaction | `@Transactional` của Jakarta | 10.3 |
| Config | `@Value` | 7.6, 14 |
| Validation | `@Valid`, `@NotBlank`, `@Size`, `@Email`, `@Pattern` | 13 |
| Custom constraint | `@NonDisposableEmail`, `@Constraint`, `@Target`, `@Retention` | 14 |
| Exception | `@RestControllerAdvice`, `@ExceptionHandler` | 15 |
| JSON | `@JsonProperty` | 9.1 |
| OpenAPI | `@OpenAPIDefinition`, `@Info`, `@Contact`, `@License`, `@Server`, `@SecurityRequirement`, `@SecurityScheme`, `@Tag`, `@Schema` | 16 |
| Lombok | `@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Builder`, `@SuperBuilder`, `@Slf4j`, `@ToString`, `@NonNull` | 18.5 |
| Java | `@Override`, khai báo `@interface` | 14, 18 |
| Test | `@SpringBootTest`, `@Test` | 17.6 |

`@OneToOne`, `@OneToMany`, `@ManyToOne`, `@Version`, `@UniqueConstraint`, `@EntityGraph`, `@Inheritance`, `@PreAuthorize` chỉ được nhắc/thêm dưới dạng kiến thức và ví dụ ở phần mở rộng, chưa phải annotation đang áp dụng trong source của dự án.

<a id="tai-lieu"></a>
## 23. Tài liệu chính thức để học tiếp

Các giải thích về nghiệp vụ và giới hạn cụ thể lấy từ source trong repository. Các liên kết dưới là tài liệu nền để đối chiếu cơ chế framework; trang reference không cố định version có thể hiển thị phiên bản mới hơn dependency thực tế. Khi debug khác biệt API, hãy kiểm tra dependency được Maven resolve thay vì mặc định trang mới nhất là đúng version đang chạy.

| Chủ đề | Nguồn |
|---|---|
| Boot startup/runner | [SpringApplication](https://docs.spring.io/spring-boot/reference/features/spring-application.html) |
| Servlet filter và đăng ký | [Spring Boot Servlet Web Applications](https://docs.spring.io/spring-boot/reference/web/servlet.html) |
| Security filter chain | [Spring Security Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html) |
| Context, Authentication, UserDetails | [Authentication Architecture](https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html) |
| Login provider | [DaoAuthenticationProvider](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/dao-authentication-provider.html) |
| Session/stateless | [Authentication Persistence and Session Management](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html) |
| CSRF | [Cross Site Request Forgery](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html) |
| URL authorization | [Authorize HTTP Requests](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html) |
| Method authorization | [Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html) |
| JWT đúng version | [JJWT README tại tag 0.12.6](https://github.com/jwtk/jjwt/blob/0.12.6/README.adoc) |
| Đọc RSA/key spec | [Java Cryptography Architecture](https://docs.oracle.com/en/java/javase/25/security/java-cryptography-architecture-jca-reference-guide.html) |
| JPA lifecycle/quan hệ | [Jakarta EE Persistence Introduction](https://jakarta.ee/learn/docs/jakartaee-tutorial/current/persist/persistence-intro/persistence-intro.html) |
| JPA save | [Spring Data JPA Persisting Entities](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html) |
| Audit metadata/listener | [Spring Data JPA Auditing](https://docs.spring.io/spring-data/jpa/reference/auditing.html) |
| Constraint validation | [Jakarta Validation 3.1](https://jakarta.ee/specifications/bean-validation/3.1/jakarta-validation-spec-3.1) |
| Regex Java | [Java SE 25 Pattern](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html) |
| MVC exception resolver | [Spring Framework Exception Handling](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html) |
| Builder kế thừa | [Lombok SuperBuilder](https://projectlombok.org/features/SuperBuilder) |

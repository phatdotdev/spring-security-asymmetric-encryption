## 1. CREATE AND CONFIG THE PROJECT
### pom.xml
````xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.2</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.doph</groupId>
	<artifactId>asymmectric-encryption</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name/>
	<description/>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>25</java.version>
		<jjwt.version>0.12.6</jjwt.version>
		<openapi.version>3.2.0</openapi.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>

		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-api</artifactId>
			<version>${jjwt.version}</version>
		</dependency>

		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-jackson</artifactId>
			<version>${jjwt.version}</version>
		</dependency>

		<dependency>
			<groupId>io.jsonwebtoken</groupId>
			<artifactId>jjwt-impl</artifactId>
			<version>${jjwt.version}</version>
		</dependency>

		<!-- Source: https://mvnrepository.com/artifact/org.springdoc/springdoc-openapi-starter-webmvc-ui -->
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>${openapi.version}</version>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<optional>true</optional>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-security-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<executions>
					<execution>
						<id>default-compile</id>
						<phase>compile</phase>
						<goals>
							<goal>compile</goal>
						</goals>
						<configuration>
							<annotationProcessorPaths>
								<path>
									<groupId>org.projectlombok</groupId>
									<artifactId>lombok</artifactId>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</execution>
					<execution>
						<id>default-testCompile</id>
						<phase>test-compile</phase>
						<goals>
							<goal>testCompile</goal>
						</goals>
						<configuration>
							<annotationProcessorPaths>
								<path>
									<groupId>org.projectlombok</groupId>
									<artifactId>lombok</artifactId>
								</path>
							</annotationProcessorPaths>
						</configuration>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>

````
## 2. ADD THE PROJECT PROPERTIES
### application.yml
````yaml
spring:
  application:
    name: asymmectric-encryption
  datasource:
    url: jdbc:postgresql://${DB_URL}:${DB_PORT}/${DB_NAME}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    database: postgresql
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    show-sql: false
    properties:
      hibernate:
        format_sql: true

app:
  security:

````
## 3. IMPLEMENT THE SECURITY CONFIG
### Security Config
````java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {


    private final JWTFilter jwtFilter;
    final String[] PUBLIC_URLS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/v2/api-docs",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-resources",
            "/swagger-resources/**",
            "/configuration/ui",
            "/configuration/security",
            "/swagger-ui/**",
            "/webjars/**",
            "/swagger-ui.html",
            "/",
            "/index.html"
    };

    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(
                        auth -> auth
                                    .requestMatchers(PUBLIC_URLS)
                                    .permitAll()
                                    .anyRequest().authenticated()
                )
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore((Filter) this.jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

}
````

## 4. IMPLEMENT THE USER ENTITY
### User 
```java
@Entity
@Table(name = "USERS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String Id;
    @Column(name = "FIRST_NAME", nullable = false)
    private String firstName;
    @Column(name = "LAST_NAME", nullable = false)
    private String lastName;
    @Column(name = "EMAIL", nullable = false, unique = true)
    private String email;
    @Column(name = "PHONE_NUMBER", nullable = false, unique = true)
    private String phoneNumber;
    @Column(name = "PASSWORD", nullable = false)
    private String password;
    @Column(name = "DATE_OF_BIRTH")
    private LocalDate dateOfBirth;
    @Column(name = "IS_ENABLED")
    private boolean enabled;
    @Column(name = "IS_ACCOUNT_CLOCKED")
    private boolean locked;
    @Column(name = "IS_CREDENTIALS_EXPIRED")
    private boolean credentialsExpired;
    @Column(name = "IS_EMAIL_VERIFIED")
    private boolean emailVerified;
    @Column(name = "IS_PHONE_VERIFIED")
    private boolean phoneVerified;

    @CreatedDate
    @Column(name = "CREATED_DATE", updatable = false, nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE", insertable = false)
    private LocalDateTime lastModifiedDate;

    @ManyToMany(
            cascade = {CascadeType.PERSIST, CascadeType.MERGE},
            fetch = FetchType.EAGER
    )
    @JoinTable(
            name = "USERS_ROLES",
            joinColumns = {
                    @JoinColumn(name = "USERS_ID"),
            },
            inverseJoinColumns = {
                    @JoinColumn(name = "ROLES_ID")
            }
    )
    private List<Role> roles;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if(CollectionUtils.isEmpty(this.roles)){
            return List.of();
        }
        return this.roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .toList();
    }

    @Override
    public String getUsername() {
        return this.getEmail();
    }

    @Override
    public String getPassword(){
        return this.getPassword();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !this.locked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !this.credentialsExpired;
    }
}
```
### BaseEntity
```java
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @CreatedDate
    @Column(name = "CREATED_DATE", updatable = false, nullable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(name = "LAST_MODIFIED_DATE", insertable = false)
    private LocalDateTime lastModifiedDate;

    @CreatedBy
    @Column(name = "CREATED_BY", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "LAST_MODIFIED_BY", insertable = false)
    private String lastModifiedBy;
}
```
### Role
```java
@Entity
@Table(name = "ROLES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Role extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String Id;

    private String name;

    @ManyToMany(mappedBy = "roles")
    private List<User> users;
}
```
## 5. IMPLEMENT THE USER SERVICE
### UserService
```Java
public interface UserService extends UserDetailsService {

    void updateProfileInformation(ProfileUpdateRequest request, String userId);

    void changePassword(ChangePasswordRequest request, String userId);

    void deactivateAccount(String userId);

    void reactivateAccount(String userId);

    void deleteAccount(String userId);
}
```
### ChangePasswordRequest
````java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChangePasswordRequest {
    private String currentPassword;
    private String newPassword;
    private String confirmNewPassword;
}

````
### ProfileUpdateRequest
````java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest {

    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;

}
````
### UserServiceImpl
````java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;


    @Override
    public void updateProfileInformation(ProfileUpdateRequest request, String userId) {
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        this.userMapper.mergerUserInfo(user, request);
        this.userRepository.save(user);
    }

    @Override
    public void changePassword(ChangePasswordRequest request, String userId) {
        if(!request.getNewPassword().equals(request.getConfirmNewPassword())){
            throw new BusinessException(ErrorCode.CHANGE_PASSWORD_MISMATCH);
        }
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        if(!this.passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())){
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        final String encoded = this.passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encoded);
        this.userRepository.save(user);
    }

    @Override
    public void deactivateAccount(String userId) {
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        if(!user.isEnabled()){
            throw new BusinessException(ErrorCode.THE_ACCOUNT_ALREADY_DEACTIVATED);
        }
        user.setEnabled(false);
        this.userRepository.save(user);
    }

    @Override
    public void reactivateAccount(String userId) {
        final User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, userId));
        if(user.isEnabled()){
            throw new BusinessException(ErrorCode.THE_ACCOUNT_ALREADY_ACTIVATED);
        }
        user.setEnabled(true);
        this.userRepository.save(user);
    }

    @Override
    public void deleteAccount(String userId) {
        // this method need the rest of the entities
        // the logic is just to schedule a profile for deletion
        // and a scheduled job will pick the profiles and delete everything
    }

    @Override
    public UserDetails loadUserByUsername(final String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCAse(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: "+email));
    }
}

````
### BeanConfig
````java
@Configuration
public class BeanConfig {

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

}
````
### UserMapper
````java
@Service
public class UserMapper {

    public void mergerUserInfo(final User user, final ProfileUpdateRequest request){
        if(StringUtils.isNotBlank(request.getFirstName())
                && !user.getFirstName().equals(request.getFirstName())
        ){
            user.setFirstName(request.getFirstName());
        }
        if(StringUtils.isNotBlank(request.getLastName())
                && !user.getLastName().equals(request.getLastName())
        ){
            user.setLastName(request.getLastName());
        }
    }

}
````
### UserRepository
````java
public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCAse(String email);

    boolean existsByPhoneNumber(String phoneNumber);

}
````
### BusinessException
````java
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] args;

    public BusinessException(final ErrorCode errorCode, final Object...args){
        super(getFormatterMessage(errorCode, args));
        this.errorCode = errorCode;
        this.args = args;
    }

    private static String getFormatterMessage(final ErrorCode errorCode, final Object[] args){
        if(args != null && args.length > 0){
            return String.format(errorCode.getDefaultMessage(), args);
        }
        return errorCode.getDefaultMessage();
    }
}
````
### ErrorCode
````java
@Getter
public enum ErrorCode {

    USER_NOT_FOUND("USER_NOT_FOUND", "User not found with id %s", HttpStatus.NOT_FOUND),
    CHANGE_PASSWORD_MISMATCH("CHANGE_PASSWORD_MISMATCH", "New password and confirm password are not the same", HttpStatus.BAD_REQUEST),
    INVALID_CURRENT_PASSWORD("INVALID_CURRENT_PASSWORD", "Current password is invalid", HttpStatus.BAD_REQUEST),
    THE_ACCOUNT_ALREADY_DEACTIVATED("THE_ACCOUNT_ALREADY_DEACTIVATED", "The account is already deactivated", HttpStatus.BAD_REQUEST ),
    THE_ACCOUNT_ALREADY_ACTIVATED("THE_ACCOUNT_ALREADY_ACTIVATED", "The account is already activated" , HttpStatus.BAD_REQUEST );

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(
            final String code,
            final String defaultMessage,
            final HttpStatus status
    ){
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

}
````
## 6. IMPLEMENT THE CONTROLLER
### UserController
````java
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "User API")
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void updateProfileInfo(
            @RequestBody @Valid final ProfileUpdateRequest request,
           final Authentication authentication
            ){
        this.userService.updateProfileInformation(request, getUserId(authentication));
    }

    @PostMapping("/me/password")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void changePassword(
            @RequestBody @Valid ChangePasswordRequest request,
            final Authentication authentication
            ){
        this.userService.changePassword(request, getUserId(authentication));
    }

    @PatchMapping("/me/deactivate")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void deactivateAccount(final Authentication authentication){
        this.userService.deactivateAccount(getUserId(authentication));
    }

    @PatchMapping("/me/activate")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void reactivateAccount(final Authentication authentication){
        this.userService.reactivateAccount(getUserId(authentication));
    }

    @DeleteMapping("/me")
    public void deleteAccount(final Authentication authentication){
        this.userService.deleteAccount(getUserId(authentication));
    }

    private String getUserId(final Authentication authentication){
        return ((User) authentication.getPrincipal()).getId();
    }
}
````
## 7. Implement the JwtService
### KeyUtils
````java
public class KeyUtils {
    private KeyUtils(){
    }

    public static PrivateKey loadPrivateKey(final String pemPath) throws NoSuchAlgorithmException, InvalidKeySpecException {
        final String key = readKeyFromResource(pemPath)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        final byte[] decoded = Base64.getDecoder().decode(key);
        final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    public static PublicKey loadPublicKey(final String pemPath) throws NoSuchAlgorithmException, InvalidKeySpecException {
        final String key = readKeyFromResource(pemPath)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        final byte[] decoded = Base64.getDecoder().decode(key);
        final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static String readKeyFromResource(final String pemPath){
        try(final InputStream is = KeyUtils.class.getResourceAsStream(pemPath)) {
            if(is == null){
                throw new IllegalArgumentException(("Could not find key file " + pemPath));
            }
            return new String(is.readAllBytes());
        }catch (IOException ex){
            ex.printStackTrace();
            return null;
        }
    }
}
````
### JwtService
````java
@Service
public class JwtService {

    private static final String TOKEN_TYPE = "token_type";
    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    @Value("${app.security.jwt.access-token-expiration}")
    private long accessTokenExpiration;
    @Value("${app.security.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public JwtService() throws NoSuchAlgorithmException, InvalidKeySpecException {
        this.privateKey = KeyUtils.loadPrivateKey("keys/local-only/private_key.pem");
        this.publicKey = KeyUtils.loadPublicKey("keys/local-only/public_key.pem");
    }

    public String generateAccessToken(final String username){
        final Map<String, Object> claims = Map.of(TOKEN_TYPE, "ACCESS_TOKEN");
        return buildToken(username, claims, this.accessTokenExpiration);
    }

    public String generateFreshToken(final String username){
        final Map<String, Object> claims = Map.of(TOKEN_TYPE, "REFRESH_TOKEN");
        return buildToken(username, claims, this.refreshTokenExpiration);
    }

    public boolean isTokenValid(final String token, final String expectedUsername){
        final String username = extractUsername(token);
        return username.equals(expectedUsername) && !isTokenExpired(token);
    }

    public String refreshAccessToken(final String refreshToken){
        final Claims claims = extractClaims(refreshToken);
        if(!"REFRESH_TOKEN".equals(claims.get("TOKEN_TYPE"))){
            throw new RuntimeException("Invalid token type");
        }
        if(isTokenExpired(refreshToken)){
            throw new RuntimeException("Refresh Token expired");
        }
        final String username = claims.getSubject();
        return generateAccessToken(username);
    }

    private String buildToken(String username, Map<String, Object> claims, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(this.privateKey)
                .compact();
    }

    private String extractUsername(final String token){
        return extractClaims(token).getSubject();
    }

    private Claims extractClaims(final String token){
        try {
            return Jwts.parser()
                    .verifyWith(this.publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (final JwtException e){
            throw new JwtException("Invalid JWT token", e);
        }
    }

    private boolean isTokenExpired(final String token){
        return extractClaims(token).getExpiration().before(new Date());
    }
}
````
### application.yml
````yaml
app:
  security:
    jwt:
      access-token-expiration: 86400000 # 24 hours
      refresh-token-expiration: 604800000 # 7 days
````
## 8. Implement JWT Filter
### JwtFilter
````java
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull
            HttpServletRequest request,
            @NonNull
            HttpServletResponse response,
            @NonNull
            FilterChain filterChain) throws ServletException, IOException {
        if(request.getServletPath().startsWith("/api/v1/auth")){
            filterChain.doFilter(request, response);
            return;
        }
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String jwt;
        final String username;
        if(authHeader == null || !authHeader.startsWith("Bearer")){
            filterChain.doFilter(request, response);
            return;
        }
        jwt = authHeader.substring(7);
        username = this.jwtService.extractUsername(jwt);
        if(username != null && SecurityContextHolder.getContext().getAuthentication() == null){
            final UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            if(this.jwtService.isTokenValid(jwt, userDetails.getUsername())){
                final UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        filterChain.doFilter(request, response);
    }
}
````
## 9. Add the Authentication DTO
### AuthenticationService
````java
public interface AuthenticationService {

    AuthenticationResponse login(AuthenticationRequest request);

    void register(RegistrationRequest request);

    AuthenticationResponse refreshToken(RefreshRequest request);

}
````
### AuthenticationRequest
````java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthenticationRequest {
    private String email;
    private String password;
}
````
### RegisterRequest
````java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RegistrationRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String password;
    private String confirmPassword;
}
````
### RefreshTokenRequest
````java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RefreshRequest {
    private String refreshToken;
}
````
### AuthenticationResponse
````java
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AuthenticationResponse {
    @JsonProperty("access_token")
    private String accessToken;
    @JsonProperty("refresh_token")
    private String refreshToken;
    @JsonProperty("token_type")
    private String tokenType;
}
````
## 10. Implement Authentication Logic
### AuthenticationServiceImpl
````java
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;

    @Override
    public AuthenticationResponse login(AuthenticationRequest request) {
        final Authentication auth = this.authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );
        final User user = (User) auth.getPrincipal();
        final String accessToken = this.jwtService.generateAccessToken(user.getUsername());
        final String refreshToken = this.jwtService.generateFreshToken(user.getUsername());
        final String tokenType = "Bearer";
        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType(tokenType)
                .build();
    }

    @Override
    @Transactional
    public void register(RegistrationRequest request) {
        checkUserEmail(request.getEmail());
        checkUserPhoneNumber(request.getPhoneNumber());
        checkPasswords(request.getPassword(), request.getConfirmPassword());

        final Role userRole = this.roleRepository.findByName("ROLE_USER")
                .orElseThrow(
                        () -> new EntityNotFoundException("Role user does not exists")
                );
        final List<Role> roles = new ArrayList<>();
        roles.add(userRole);

        final User user = this.userMapper.toUser(request);
        user.setRoles(roles);
        log.debug("Saving user {}", user);
        this.userRepository.save(user);

        final List<User> users = new ArrayList<>();
        users.add(user);
        userRole.setUsers(users);

        this.roleRepository.save(userRole);
    }

    @Override
    public AuthenticationResponse refreshToken(RefreshRequest request) {
        final String newAccessToken = this.jwtService.refreshAccessToken(request.getRefreshToken());
        final String tokenType = "Bearer";
        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .tokenType(tokenType)
                .build();
    }

    private void checkUserEmail(String email){
        final boolean emailExists = this.userRepository.existsByEmailIgnoreCase(email);
        if(emailExists){
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    private void checkUserPhoneNumber(String phoneNumber){
        final boolean phoneNumberExists = this.userRepository.existsByPhoneNumber(phoneNumber);
        if(phoneNumberExists){
            throw new BusinessException(ErrorCode.PHONE_ALREADY_EXISTS);
        }
    }

    private void checkPasswords(String password, String confirmPassword){
        if(password == null || !password.equals(confirmPassword)){
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }
    }
}
````
### BeanConfig
````java
@Configuration
public class BeanConfig {

    @Bean
    PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(final AuthenticationConfiguration config){
        return config.getAuthenticationManager();
    }
}
````
### RoleRepository
````java
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByName(String roleUser);
}
````
### UserMapper
````java
  public User toUser(RegistrationRequest request) {
        return User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phoneNumber(request.getPhoneNumber())
                .password(request.getPassword())
                .enabled(true)
                .locked(false)
                .credentialsExpired(false)
                .emailVerified(false)
                .phoneVerified(false)
                .build();
    }
````
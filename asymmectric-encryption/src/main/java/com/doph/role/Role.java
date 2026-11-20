package com.doph.role;

import com.doph.common.BaseEntity;
import com.doph.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;

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

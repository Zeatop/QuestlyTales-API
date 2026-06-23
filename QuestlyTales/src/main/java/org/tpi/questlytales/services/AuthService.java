package org.tpi.questlytales.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.tpi.questlytales.dtos.userdtos.AuthResponseDTO;
import org.tpi.questlytales.dtos.userdtos.LoginRequestDTO;
import org.tpi.questlytales.dtos.userdtos.RegisterRequestDTO;
import org.tpi.questlytales.models.User;

import java.util.Date;
import java.util.List;

@Service
public class AuthService implements UserDetailsService {

    @Qualifier("usersMongoTemplate")
    @Autowired
    private MongoTemplate usersMongoTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    public AuthResponseDTO register(RegisterRequestDTO request) {
        Query query = new Query(Criteria.where("email").is(request.getEmail()));
        if (usersMongoTemplate.exists(query, User.class)) {
            throw new RuntimeException("Email déjà utilisé");
        }

        User user = User.builder()
            .nom(request.getNom())
            .prenom(request.getPrenom())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .createdAt(new Date())
            .build();

        usersMongoTemplate.insert(user);

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponseDTO(token, user.getId(), user.getEmail());
    }

    public AuthResponseDTO login(LoginRequestDTO request) {
        Query query = new Query(Criteria.where("email").is(request.getEmail()));
        User user = usersMongoTemplate.findOne(query, User.class);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Email ou mot de passe invalide");
        }

        String token = jwtService.generateToken(user.getEmail(), user.getId());
        return new AuthResponseDTO(token, user.getId(), user.getEmail());
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Query query = new Query(Criteria.where("email").is(email));
        User user = usersMongoTemplate.findOne(query, User.class);

        if (user == null) {
            throw new UsernameNotFoundException("Utilisateur non trouvé: " + email);
        }

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPassword())
            .authorities(List.of())
            .build();
    }
}

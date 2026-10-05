package com.example.spring_security_jwt.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.example.spring_security_jwt.entities.ERole;
import com.example.spring_security_jwt.entities.Role;
import com.example.spring_security_jwt.entities.User;
import com.example.spring_security_jwt.repository.UserRepository;

/* Test unitario del servicio que usa Spring Security para cargar al usuario que hace login.
 * En este proyecto el login es por EMAIL: loadUserByUsername recibe el email. */
@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserDetailsServiceImpl userDetailsService;

	private User admin;

	@BeforeEach
	void setUp() {

		admin = User.builder()
				.id(1L)
				.username("admin")
				.email("admin@example.com")
				.password("password-encriptada")
				.roles(Set.of(Role.builder().id(1).name(ERole.ROLE_ADMIN).build()))
				.build();
	}

	@Test
	@DisplayName("Test cargar un usuario existente por su email")
	void testLoadUserByUsernameEncontrado() {

		// given
		given(userRepository.findByEmail("admin@example.com")).willReturn(Optional.of(admin));

		// when
		UserDetails userDetails = userDetailsService.loadUserByUsername("admin@example.com");

		// then
		assertThat(userDetails.getUsername()).isEqualTo("admin");
		assertThat(userDetails.getPassword()).isEqualTo("password-encriptada");
		assertThat(userDetails.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
				.containsExactly("ROLE_ADMIN");

		/* UserDetailsImpl tambien guarda el id y el email (los usa AuthController en la respuesta) */
		assertThat(userDetails).isInstanceOf(UserDetailsImpl.class);
		UserDetailsImpl userDetailsImpl = (UserDetailsImpl) userDetails;
		assertThat(userDetailsImpl.getId()).isEqualTo(1L);
		assertThat(userDetailsImpl.getEmail()).isEqualTo("admin@example.com");

		verify(userRepository).findByEmail("admin@example.com");
	}

	@Test
	@DisplayName("Test cargar un usuario que no existe: lanza UsernameNotFoundException")
	void testLoadUserByUsernameNoEncontrado() {

		// given
		given(userRepository.findByEmail("noexiste@example.com")).willReturn(Optional.empty());

		// when / then
		assertThatThrownBy(() -> userDetailsService.loadUserByUsername("noexiste@example.com"))
				.isInstanceOf(UsernameNotFoundException.class)
				.hasMessageContaining("noexiste@example.com");
	}
}

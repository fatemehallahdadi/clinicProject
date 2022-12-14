package ir.mapsa.clinic.resources;

import ir.mapsa.clinic.entity.ClinicUser;
import ir.mapsa.clinic.entity.EmployeeEntity;
import ir.mapsa.clinic.entity.Role;
import ir.mapsa.clinic.entity.enums.ERole;
import ir.mapsa.clinic.exceptions.BaseException;
import ir.mapsa.clinic.payload.request.LoginRequest;
import ir.mapsa.clinic.payload.request.SignupRequest;
import ir.mapsa.clinic.payload.response.JwtResponse;
import ir.mapsa.clinic.payload.response.MessageResponse;
import ir.mapsa.clinic.repository.RoleRepository;
import ir.mapsa.clinic.security.jwt.JwtUtils;
import ir.mapsa.clinic.security.services.UserDetailsImpl;
import ir.mapsa.clinic.service.ClinicUserService;
import ir.mapsa.clinic.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final ClinicUserService userService;

    private final EmployeeService employeeService;

    private final RoleRepository roleRepository;

    private final PasswordEncoder encoder;

    private final JwtUtils jwtUtils;

    @PostMapping("/signin")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(item -> item.getAuthority())
                .collect(Collectors.toList());

        return ResponseEntity.ok(new JwtResponse(jwt,
                userDetails.getId(),
                userDetails.getUsername(),
                roles));
    }

    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@Valid @RequestBody SignupRequest signUpRequest) throws BaseException {
        if (userService.existsByEmail(signUpRequest.getEmail())) {
            return ResponseEntity
                    .badRequest()
                    .body(new MessageResponse("Error: email is already taken!"));
        }
        Set<Role> roles = new HashSet<>();
        roles.add(roleRepository.findByRole(ERole.ROLE_USER).orElse(null));

//        // Creating a new user account
//        ClinicUser user = new ClinicUser(
//                signUpRequest.getFirstName(),
//                signUpRequest.getLastName(),
//                signUpRequest.getEmail(),
//                encoder.encode(signUpRequest.getPassword())
//        );
        ClinicUser user = ClinicUser.builder().firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(signUpRequest.getEmail())
                .password(encoder.encode(signUpRequest.getPassword()))
                .roles(roles).build();
        ClinicUser savedUser = userService.save(user);

        EmployeeEntity employee = EmployeeEntity.builder().firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .email(signUpRequest.getEmail()).build();
        EmployeeEntity savedEmployee = employeeService.saveOrUpdate(employee);

        savedUser.setEmployee(savedEmployee);
        userService.save(savedUser);

        savedEmployee.setClinicUser(savedUser);
        employeeService.saveOrUpdate(savedEmployee);

        return ResponseEntity.ok(new MessageResponse("User registered successfully!"));
    }
}

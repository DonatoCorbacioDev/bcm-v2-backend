package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.RiskFeedbackDTO;
import com.donatodev.bcm_backend.dto.RiskFeedbackRequest;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.RiskFeedback;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.AccessDeniedException;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.mapper.RiskFeedbackMapper;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.RiskFeedbackRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class RiskFeedbackServiceTest {

    @Mock
    private RiskFeedbackRepository riskFeedbackRepository;

    @Mock
    private ContractsRepository contractsRepository;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private RiskFeedbackMapper riskFeedbackMapper;

    @InjectMocks
    private RiskFeedbackService riskFeedbackService;

    @AfterEach
    @SuppressWarnings("unused")
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: RiskFeedbackService")
    @SuppressWarnings("unused")
    class VerifyRiskFeedbackService {

        @Test
        @Order(1)
        @DisplayName("create as ADMIN persists feedback for any contract")
        void shouldCreateAsAdmin() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            Contracts contract = Contracts.builder().id(1L)
                    .organization(Organization.builder().id(9L).build())
                    .build();
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.8, "HIGH", 0.75, "HIGH", true);
            RiskFeedback saved = RiskFeedback.builder().id(1L).contract(contract).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(1L, 1L, 0.8, "HIGH", 0.75, "HIGH", true, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(
                            new User("admin", "pwd", List.of(() -> "ROLE_ADMIN")), null, List.of(() -> "ROLE_ADMIN")
                    )
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
            when(riskFeedbackRepository.save(org.mockito.ArgumentMatchers.any(RiskFeedback.class))).thenReturn(saved);
            when(riskFeedbackMapper.toDTO(saved)).thenReturn(dto);

            RiskFeedbackDTO result = riskFeedbackService.create(1L, request);

            assertEquals(1L, result.id());
            assertEquals("HIGH", result.riskLevel());
        }

        @Test
        @Order(2)
        @DisplayName("create as MANAGER on owned contract succeeds")
        void shouldCreateAsManagerOwner() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder().username("manager").role(Roles.builder().role("MANAGER").build()).manager(manager).build();
            Contracts contract = Contracts.builder().id(1L).manager(manager).build();
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.4, "MEDIUM", null, null, false);
            RiskFeedback saved = RiskFeedback.builder().id(2L).contract(contract).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(2L, 1L, 0.4, "MEDIUM", null, null, false, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));
            when(riskFeedbackRepository.save(org.mockito.ArgumentMatchers.any(RiskFeedback.class))).thenReturn(saved);
            when(riskFeedbackMapper.toDTO(saved)).thenReturn(dto);

            RiskFeedbackDTO result = riskFeedbackService.create(1L, request);

            assertEquals(2L, result.id());
            assertEquals(false, result.agree());
        }

        @Test
        @Order(3)
        @DisplayName("create as MANAGER on a contract they don't own throws AccessDeniedException")
        void shouldThrowWhenManagerNotOwner() {
            Managers otherManager = Managers.builder().id(99L).build();
            Managers managerEntity = Managers.builder().id(5L).build();
            Users managerUser = Users.builder().username("manager").role(Roles.builder().role("MANAGER").build()).manager(managerEntity).build();
            Contracts contract = Contracts.builder().id(1L).manager(otherManager).build();
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.4, "MEDIUM", null, null, false);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(contractsRepository.findById(1L)).thenReturn(Optional.of(contract));

            assertThrows(AccessDeniedException.class, () -> riskFeedbackService.create(1L, request));
        }

        @Test
        @Order(4)
        @DisplayName("create throws ContractNotFoundException when contract missing")
        void shouldThrowWhenContractMissing() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.4, "MEDIUM", null, null, false);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "ROLE_ADMIN"))
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class, () -> riskFeedbackService.create(999L, request));
        }

        @Test
        @Order(5)
        @DisplayName("create with TenantContext scopes contract lookup to the organization")
        void shouldCreateWithTenantContext() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            Contracts contract = Contracts.builder().id(1L).organization(Organization.builder().id(2L).build()).build();
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.4, "MEDIUM", null, null, true);
            RiskFeedback saved = RiskFeedback.builder().id(3L).contract(contract).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(3L, 1L, 0.4, "MEDIUM", null, null, true, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "ROLE_ADMIN"))
            );

            TenantContext.set(2L);
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findByIdAndOrganization_Id(1L, 2L)).thenReturn(Optional.of(contract));
            when(riskFeedbackRepository.save(org.mockito.ArgumentMatchers.any(RiskFeedback.class))).thenReturn(saved);
            when(riskFeedbackMapper.toDTO(saved)).thenReturn(dto);

            RiskFeedbackDTO result = riskFeedbackService.create(1L, request);

            assertEquals(3L, result.id());
            verify(contractsRepository).findByIdAndOrganization_Id(1L, 2L);
        }

        @Test
        @Order(6)
        @DisplayName("create stores the organization id resolved from TenantContext when the contract has none")
        void shouldFallBackToTenantContextOrgId() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            Contracts contract = Contracts.builder().id(1L).build();
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.4, "MEDIUM", null, null, true);
            RiskFeedback saved = RiskFeedback.builder().id(4L).contract(contract).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(4L, 1L, 0.4, "MEDIUM", null, null, true, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "ROLE_ADMIN"))
            );

            TenantContext.set(7L);
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(contractsRepository.findByIdAndOrganization_Id(1L, 7L)).thenReturn(Optional.of(contract));
            ArgumentCaptor<RiskFeedback> captor = ArgumentCaptor.forClass(RiskFeedback.class);
            when(riskFeedbackRepository.save(captor.capture())).thenReturn(saved);
            when(riskFeedbackMapper.toDTO(saved)).thenReturn(dto);

            riskFeedbackService.create(1L, request);

            assertEquals(7L, captor.getValue().getOrganizationId());
        }

        @Test
        @Order(7)
        @DisplayName("getFeedbackForCurrentUser as ADMIN with TenantContext uses org-scoped repository")
        void shouldGetFeedbackAsAdminWithTenant() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            RiskFeedback entity = RiskFeedback.builder().id(1L).contract(Contracts.builder().id(1L).build()).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(1L, 1L, 0.8, "HIGH", null, null, true, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "ROLE_ADMIN"))
            );

            TenantContext.set(2L);
            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(riskFeedbackRepository.findByOrganizationIdOrderByCreatedAtDescIdDesc(2L)).thenReturn(List.of(entity));
            when(riskFeedbackMapper.toDTO(entity)).thenReturn(dto);

            List<RiskFeedbackDTO> result = riskFeedbackService.getFeedbackForCurrentUser();

            assertEquals(1, result.size());
            verify(riskFeedbackRepository).findByOrganizationIdOrderByCreatedAtDescIdDesc(2L);
        }

        @Test
        @Order(8)
        @DisplayName("getFeedbackForCurrentUser as ADMIN without TenantContext falls back to findAll")
        void shouldGetFeedbackAsAdminWithoutTenant() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            RiskFeedback entity = RiskFeedback.builder().id(1L).contract(Contracts.builder().id(1L).build()).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(1L, 1L, 0.8, "HIGH", null, null, true, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "ROLE_ADMIN"))
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            when(riskFeedbackRepository.findAll()).thenReturn(List.of(entity));
            when(riskFeedbackMapper.toDTO(entity)).thenReturn(dto);

            List<RiskFeedbackDTO> result = riskFeedbackService.getFeedbackForCurrentUser();

            assertEquals(1, result.size());
        }

        @Test
        @Order(9)
        @DisplayName("getFeedbackForCurrentUser as MANAGER scopes by manager id")
        void shouldGetFeedbackAsManager() {
            Managers manager = Managers.builder().id(5L).build();
            Users managerUser = Users.builder().username("manager").role(Roles.builder().role("MANAGER").build()).manager(manager).build();
            RiskFeedback entity = RiskFeedback.builder().id(1L).contract(Contracts.builder().id(1L).manager(manager).build()).build();
            RiskFeedbackDTO dto = new RiskFeedbackDTO(1L, 1L, 0.8, "HIGH", null, null, true, LocalDateTime.of(2027, Month.JUNE, 1, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("manager", null, List.of(() -> "ROLE_MANAGER"))
            );

            when(usersRepository.findByUsername("manager")).thenReturn(Optional.of(managerUser));
            when(riskFeedbackRepository.findByContractManagerIdOrderByCreatedAtDescIdDesc(5L)).thenReturn(List.of(entity));
            when(riskFeedbackMapper.toDTO(entity)).thenReturn(dto);

            List<RiskFeedbackDTO> result = riskFeedbackService.getFeedbackForCurrentUser();

            assertEquals(1, result.size());
            verify(riskFeedbackRepository).findByContractManagerIdOrderByCreatedAtDescIdDesc(5L);
        }

        @Test
        @Order(10)
        @DisplayName("getFeedbackForCurrentUser keeps only the most recent entry per contract")
        void shouldDedupeToLatestPerContract() {
            Users admin = Users.builder().username("admin").role(Roles.builder().role("ADMIN").build()).build();
            Contracts contract = Contracts.builder().id(1L).build();
            RiskFeedback newer = RiskFeedback.builder().id(2L).contract(contract).agree(true)
                    .createdAt(LocalDateTime.of(2027, Month.JUNE, 2, 0, 0)).build();
            RiskFeedback older = RiskFeedback.builder().id(1L).contract(contract).agree(false)
                    .createdAt(LocalDateTime.of(2027, Month.JUNE, 1, 0, 0)).build();
            RiskFeedbackDTO newerDto = new RiskFeedbackDTO(2L, 1L, 0.8, "HIGH", null, null, true, LocalDateTime.of(2027, Month.JUNE, 2, 0, 0));

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin", null, List.of(() -> "ROLE_ADMIN"))
            );

            when(usersRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
            // findAll() has no defined order; the service must sort by createdAt/id
            // itself, so feed it the entries in a deliberately non-sorted order.
            when(riskFeedbackRepository.findAll()).thenReturn(List.of(older, newer));
            when(riskFeedbackMapper.toDTO(newer)).thenReturn(newerDto);

            List<RiskFeedbackDTO> result = riskFeedbackService.getFeedbackForCurrentUser();

            assertEquals(1, result.size());
            assertEquals(2L, result.get(0).id());
        }

        @Test
        @Order(11)
        @DisplayName("create throws UserNotFoundException when authenticated user does not exist")
        void shouldThrowUserNotFoundOnCreate() {
            RiskFeedbackRequest request = new RiskFeedbackRequest(0.4, "MEDIUM", null, null, true);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("ghost", null, List.of(() -> "ROLE_ADMIN"))
            );

            when(usersRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            UserNotFoundException ex = assertThrows(UserNotFoundException.class, () -> riskFeedbackService.create(1L, request));
            assertNotNull(ex);
        }

        @Test
        @Order(12)
        @DisplayName("getFeedbackForCurrentUser throws UserNotFoundException when not authenticated")
        void shouldThrowUserNotFoundOnGet() {
            SecurityContextHolder.clearContext();
            assertThrows(UserNotFoundException.class, () -> riskFeedbackService.getFeedbackForCurrentUser());
        }
    }
}

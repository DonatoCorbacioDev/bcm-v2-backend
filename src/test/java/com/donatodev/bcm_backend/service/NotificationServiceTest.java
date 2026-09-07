package com.donatodev.bcm_backend.service;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.dto.NotificationDTO;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Counterparty;
import com.donatodev.bcm_backend.entity.CounterpartyType;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Notification;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.ContractNotFoundException;
import com.donatodev.bcm_backend.exception.NotificationNotFoundException;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.NotificationRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UsersRepository usersRepository;
    @Mock private ContractsRepository contractsRepository;

    @InjectMocks private NotificationService notificationService;

    private static final Long USER_ID = 1L;
    private static final Long ORG_ID  = 10L;
    private static final String USERNAME = "testuser";

    private static Roles role(String name) {
        Roles role = new Roles();
        role.setRole(name);
        return role;
    }

    @BeforeEach
    void setupContext() {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new UsernamePasswordAuthenticationToken(USERNAME, null, Collections.emptyList()));
        SecurityContextHolder.setContext(ctx);
        TenantContext.set(ORG_ID);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("Unit Test: NotificationService")
    @SuppressWarnings("unused")
    class VerifyNotificationService {

        @Test
        @Order(1)
        @DisplayName("Should create notification for existing user")
        void shouldCreateNotificationForExistingUser() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            when(usersRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            notificationService.createForUser(USER_ID, ORG_ID, "Title", "Message", NotificationType.INFO);

            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @Order(2)
        @DisplayName("Should throw UserNotFoundException when user not found on create")
        void shouldThrowWhenUserNotFoundOnCreate() {
            when(usersRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> notificationService.createForUser(99L, ORG_ID, "T", "M", NotificationType.INFO));

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @Order(3)
        @DisplayName("Should return mapped DTOs for current user")
        void shouldReturnMappedDTOsForCurrentUser() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            Notification n = Notification.builder()
                    .id(1L).user(user).orgId(ORG_ID)
                    .title("T").message("M").type(NotificationType.WARNING)
                    .read(false).createdAt(LocalDateTime.of(2027, Month.JUNE, 15, 12, 0)).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(notificationRepository.findByUserIdAndOrgIdOrderByCreatedAtDesc(USER_ID, ORG_ID))
                    .thenReturn(List.of(n));

            List<NotificationDTO> result = notificationService.getForCurrentUser();

            assertEquals(1, result.size());
            assertEquals("T", result.get(0).title());
            assertEquals(NotificationType.WARNING, result.get(0).type());
        }

        @Test
        @Order(4)
        @DisplayName("Should return empty list when no notifications")
        void shouldReturnEmptyListWhenNoNotifications() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(notificationRepository.findByUserIdAndOrgIdOrderByCreatedAtDesc(USER_ID, ORG_ID))
                    .thenReturn(Collections.emptyList());

            List<NotificationDTO> result = notificationService.getForCurrentUser();

            assertEquals(0, result.size());
        }

        @Test
        @Order(5)
        @DisplayName("Should mark notification as read")
        void shouldMarkNotificationAsRead() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            Notification n = Notification.builder()
                    .id(1L).user(user).orgId(ORG_ID).title("T").message("M")
                    .type(NotificationType.INFO).read(false).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            notificationService.markAsRead(1L);

            verify(notificationRepository).save(n);
            assertEquals(true, n.isRead());
        }

        @Test
        @Order(6)
        @DisplayName("Should throw NotificationNotFoundException when notification missing")
        void shouldThrowWhenNotificationNotFound() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(NotificationNotFoundException.class,
                    () -> notificationService.markAsRead(99L));
        }

        @Test
        @Order(7)
        @DisplayName("Should throw AccessDeniedException for wrong user")
        void shouldThrowWhenNotificationBelongsToAnotherUser() {
            Users owner = Users.builder().id(2L).username("otheruser").build();
            Users requester = Users.builder().id(USER_ID).username(USERNAME).build();
            Notification n = Notification.builder()
                    .id(1L).user(owner).orgId(ORG_ID).title("T").message("M")
                    .type(NotificationType.INFO).read(false).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(requester));
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            assertThrows(AccessDeniedException.class,
                    () -> notificationService.markAsRead(1L));
        }

        @Test
        @Order(8)
        @DisplayName("Should mark all unread notifications as read")
        void shouldMarkAllUnreadAsRead() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            Notification n1 = Notification.builder()
                    .id(1L).user(user).orgId(ORG_ID).title("T1").message("M1")
                    .type(NotificationType.INFO).read(false).build();
            Notification n2 = Notification.builder()
                    .id(2L).user(user).orgId(ORG_ID).title("T2").message("M2")
                    .type(NotificationType.WARNING).read(true).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(notificationRepository.findByUserIdAndOrgIdOrderByCreatedAtDesc(USER_ID, ORG_ID))
                    .thenReturn(List.of(n1, n2));

            notificationService.markAllAsRead();

            verify(notificationRepository).saveAll(any());
            assertEquals(true, n1.isRead());
        }

        @Test
        @Order(9)
        @DisplayName("Should return unread count")
        void shouldReturnUnreadCount() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(notificationRepository.countByUserIdAndOrgIdAndReadFalse(USER_ID, ORG_ID)).thenReturn(3L);

            long count = notificationService.getUnreadCount();

            assertEquals(3L, count);
            verify(notificationRepository).countByUserIdAndOrgIdAndReadFalse(USER_ID, ORG_ID);
        }

        @Test
        @Order(10)
        @DisplayName("Should throw UserNotFoundException when current user not found")
        void shouldThrowWhenCurrentUserNotFound() {
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

            assertThrows(UserNotFoundException.class,
                    () -> notificationService.getForCurrentUser());
        }

        @Test
        @Order(11)
        @DisplayName("Should throw AccessDeniedException when user matches but org does not")
        void shouldThrowWhenOrgDoesNotMatch() {
            Users owner = Users.builder().id(USER_ID).username(USERNAME).build();
            Notification n = Notification.builder()
                    .id(1L).user(owner).orgId(99L)
                    .title("T").message("M").type(NotificationType.INFO).read(false).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(owner));
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            assertThrows(AccessDeniedException.class,
                    () -> notificationService.markAsRead(1L));
        }

        @Test
        @Order(12)
        @DisplayName("Should create a reminder when the contract belongs to the caller's org")
        void shouldCreateReminderWhenContractInOrg() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).role(role("ADMIN")).build();
            Contracts contract = Contracts.builder().id(5L).counterparty(Counterparty.builder().name("Acme").type(CounterpartyType.CUSTOMER).build()).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(contractsRepository.findByIdAndOrganization_Id(5L, ORG_ID)).thenReturn(Optional.of(contract));

            notificationService.createReminderForCurrentUser(5L, "Rinnovo in scadenza");

            verify(notificationRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                    "Promemoria: Acme".equals(saved.getTitle())
                            && "Rinnovo in scadenza".equals(saved.getMessage())
                            && ORG_ID.equals(saved.getOrgId())));
        }

        @Test
        @Order(13)
        @DisplayName("Should throw ContractNotFoundException when the contract is not in the caller's org")
        void shouldThrowWhenContractNotInOrg() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).role(role("ADMIN")).build();
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(contractsRepository.findByIdAndOrganization_Id(999L, ORG_ID)).thenReturn(Optional.empty());

            assertThrows(ContractNotFoundException.class,
                    () -> notificationService.createReminderForCurrentUser(999L, "hi"));

            verify(notificationRepository, never()).save(any());
        }

        @Test
        @Order(14)
        @DisplayName("Should truncate a title longer than 200 characters")
        void shouldTruncateLongReminderTitle() {
            Users user = Users.builder().id(USER_ID).username(USERNAME).role(role("ADMIN")).build();
            String longName = "A".repeat(250);
            Contracts contract = Contracts.builder().id(6L)
                    .counterparty(Counterparty.builder().name(longName).type(CounterpartyType.CUSTOMER).build())
                    .build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(contractsRepository.findByIdAndOrganization_Id(6L, ORG_ID)).thenReturn(Optional.of(contract));

            notificationService.createReminderForCurrentUser(6L, "hi");

            verify(notificationRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                    saved.getTitle().length() == 200));
        }

        @Test
        @Order(15)
        @DisplayName("Should fall back to an unscoped contract lookup when TenantContext is empty")
        void shouldCreateReminderWhenOrgContextIsAbsent() {
            TenantContext.clear();
            Users user = Users.builder().id(USER_ID).username(USERNAME).role(role("ADMIN")).build();
            Contracts contract = Contracts.builder().id(7L).counterparty(Counterparty.builder().name("Beta").type(CounterpartyType.CUSTOMER).build()).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(contractsRepository.findById(7L)).thenReturn(Optional.of(contract));

            notificationService.createReminderForCurrentUser(7L, "hi");

            verify(notificationRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                    "Promemoria: Beta".equals(saved.getTitle()) && saved.getOrgId() == null));
        }

        // Regression tests for B1: a MANAGER confirming an agent-proposed
        // reminder must be re-validated against their own assigned contracts,
        // not just the caller's org — the ML side only checked org membership.

        @Test
        @Order(16)
        @DisplayName("Should create a reminder when the contract is assigned to the manager")
        void shouldCreateReminderWhenContractAssignedToManager() {
            Managers manager = Managers.builder().id(42L).build();
            Users user = Users.builder().id(USER_ID).username(USERNAME).role(role("MANAGER")).manager(manager).build();
            Contracts contract = Contracts.builder().id(5L).counterparty(Counterparty.builder().name("Acme").type(CounterpartyType.CUSTOMER).build()).manager(manager).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(contractsRepository.findByIdAndOrganization_Id(5L, ORG_ID)).thenReturn(Optional.of(contract));

            notificationService.createReminderForCurrentUser(5L, "Rinnovo in scadenza");

            verify(notificationRepository).save(org.mockito.ArgumentMatchers.argThat(saved ->
                    "Promemoria: Acme".equals(saved.getTitle())));
        }

        @Test
        @Order(17)
        @DisplayName("Should throw ContractNotFoundException when the contract belongs to another manager")
        void shouldThrowWhenContractBelongsToAnotherManager() {
            Managers callerManager = Managers.builder().id(42L).build();
            Managers otherManager = Managers.builder().id(99L).build();
            Users user = Users.builder().id(USER_ID).username(USERNAME).role(role("MANAGER")).manager(callerManager).build();
            Contracts contract = Contracts.builder().id(5L).counterparty(Counterparty.builder().name("Acme").type(CounterpartyType.CUSTOMER).build()).manager(otherManager).build();

            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
            when(contractsRepository.findByIdAndOrganization_Id(5L, ORG_ID)).thenReturn(Optional.of(contract));

            assertThrows(ContractNotFoundException.class,
                    () -> notificationService.createReminderForCurrentUser(5L, "hi"));

            verify(notificationRepository, never()).save(any());
        }
    }
}

package com.donatodev.bcm_backend.service;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.ContractStatus;
import com.donatodev.bcm_backend.entity.Contracts;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.repository.ContractsRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class CalendarFeedServiceTest {

    private static final Long ORG_ID = 5L;
    private static final String USERNAME = "admin";
    private static final String BACKEND_URL = "http://localhost:8090/api/v1";

    @Mock private UsersRepository usersRepository;
    @Mock private ContractsRepository contractsRepository;

    private CalendarFeedService calendarFeedService;

    @BeforeEach
    void setup() {
        calendarFeedService = new CalendarFeedService(
                usersRepository, contractsRepository, new CurrentUserResolver(usersRepository));
        ReflectionTestUtils.setField(calendarFeedService, "backendBaseUrl", BACKEND_URL);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private Users adminUser(String calendarToken) {
        Organization org = Organization.builder().id(ORG_ID).name("Acme").build();
        Roles adminRole = Roles.builder().id(1L).role("ADMIN").build();
        return Users.builder().id(1L).username(USERNAME).role(adminRole)
                .organization(org).calendarToken(calendarToken).build();
    }

    private Users managerUser(Long managerId, String calendarToken) {
        Roles managerRole = Roles.builder().id(2L).role("MANAGER").build();
        Managers manager = Managers.builder().id(managerId).build();
        return Users.builder().id(2L).username("manager1").role(managerRole)
                .manager(manager).calendarToken(calendarToken).build();
    }

    private Contracts contract(Long id, String number, String customer, LocalDate endDate) {
        Contracts c = new Contracts();
        c.setId(id);
        c.setContractNumber(number);
        c.setCustomerName(customer);
        c.setEndDate(endDate);
        c.setStatus(ContractStatus.ACTIVE);
        return c;
    }

    @Nested
    @DisplayName("getOrCreateFeedUrl / regenerateFeedUrl")
    class TokenManagement {

        @Test
        @DisplayName("generates and saves a token when the user doesn't have one yet")
        void shouldGenerateTokenWhenMissing() {
            authenticateAs(USERNAME);
            TenantContext.set(ORG_ID);
            Users user = adminUser(null);
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            String url = calendarFeedService.getOrCreateFeedUrl();

            assertTrue(url.startsWith(BACKEND_URL + "/calendar/"));
            assertTrue(url.endsWith(".ics"));
            verify(usersRepository).save(user);
            assertNotNull(user.getCalendarToken());
        }

        @Test
        @DisplayName("reuses the existing token without saving again")
        void shouldReuseExistingToken() {
            authenticateAs(USERNAME);
            TenantContext.set(ORG_ID);
            Users user = adminUser("existing-token-1234");
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            String url = calendarFeedService.getOrCreateFeedUrl();

            assertEquals(BACKEND_URL + "/calendar/existing-token-1234.ics", url);
            verify(usersRepository, never()).save(any());
        }

        @Test
        @DisplayName("regenerateFeedUrl always issues a new token, invalidating the old URL")
        void shouldRegenerateToken() {
            authenticateAs(USERNAME);
            TenantContext.set(ORG_ID);
            Users user = adminUser("old-token");
            when(usersRepository.findByUsernameAndOrganizationId(USERNAME, ORG_ID)).thenReturn(Optional.of(user));

            String url = calendarFeedService.regenerateFeedUrl();

            assertNotEquals(BACKEND_URL + "/calendar/old-token.ics", url);
            verify(usersRepository).save(user);
        }

        @Test
        @DisplayName("falls back to findByUsername when TenantContext is empty")
        void shouldFallBackWhenTenantContextEmpty() {
            authenticateAs(USERNAME);
            Users user = adminUser(null);
            when(usersRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

            String url = calendarFeedService.getOrCreateFeedUrl();

            assertTrue(url.startsWith(BACKEND_URL + "/calendar/"));
        }

        @Test
        @DisplayName("throws when there is no authenticated user")
        void shouldThrowWhenNotAuthenticated() {
            assertThrows(UserNotFoundException.class, () -> calendarFeedService.getOrCreateFeedUrl());
        }
    }

    @Nested
    @DisplayName("buildIcsFeed")
    class BuildIcsFeed {

        @Test
        @DisplayName("throws when the token doesn't match any user")
        void shouldThrowForUnknownToken() {
            when(usersRepository.findByCalendarToken("bogus")).thenReturn(Optional.empty());
            assertThrows(UserNotFoundException.class, () -> calendarFeedService.buildIcsFeed("bogus"));
        }

        @Test
        @DisplayName("admin: includes all ACTIVE contracts in the organization, one VEVENT each")
        void shouldBuildFeedForAdmin() {
            Users user = adminUser("token-1");
            when(usersRepository.findByCalendarToken("token-1")).thenReturn(Optional.of(user));
            when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG_ID))
                    .thenReturn(List.of(
                            contract(1L, "CTR-001", "Acme Corp", LocalDate.of(2026, Month.AUGUST, 15))));

            String ics = calendarFeedService.buildIcsFeed("token-1");

            assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
            assertTrue(ics.contains("BEGIN:VEVENT\r\n"));
            assertTrue(ics.contains("UID:contract-1@bcm\r\n"));
            assertTrue(ics.contains("DTSTART;VALUE=DATE:20260815\r\n"));
            assertTrue(ics.contains("DTEND;VALUE=DATE:20260816\r\n"));
            assertTrue(ics.contains("SUMMARY:Scadenza contratto CTR-001 - Acme Corp\r\n"));
            assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        }

        @Test
        @DisplayName("admin with no organization: includes all ACTIVE contracts, unscoped")
        void shouldBuildFeedForAdminWithoutOrganization() {
            Roles adminRole = Roles.builder().id(1L).role("ADMIN").build();
            Users user = Users.builder().id(1L).username(USERNAME).role(adminRole)
                    .calendarToken("token-no-org").build();
            when(usersRepository.findByCalendarToken("token-no-org")).thenReturn(Optional.of(user));
            when(contractsRepository.findByStatus(ContractStatus.ACTIVE))
                    .thenReturn(List.of(
                            contract(5L, "CTR-005", "Delta", LocalDate.of(2026, Month.NOVEMBER, 20))));

            String ics = calendarFeedService.buildIcsFeed("token-no-org");

            assertTrue(ics.contains("UID:contract-5@bcm\r\n"));
            verify(contractsRepository, never()).findByStatusAndOrganization_Id(any(), any());
        }

        @Test
        @DisplayName("manager: scoped to their own assigned contracts only")
        void shouldBuildFeedForManager() {
            Users user = managerUser(42L, "token-2");
            when(usersRepository.findByCalendarToken("token-2")).thenReturn(Optional.of(user));
            when(contractsRepository.findByManagerIdAndStatus(42L, ContractStatus.ACTIVE))
                    .thenReturn(List.of(
                            contract(2L, "CTR-002", "Beta Srl", LocalDate.of(2026, Month.SEPTEMBER, 1))));

            String ics = calendarFeedService.buildIcsFeed("token-2");

            assertTrue(ics.contains("UID:contract-2@bcm\r\n"));
            verify(contractsRepository, never()).findByStatusAndOrganization_Id(any(), any());
        }

        @Test
        @DisplayName("skips contracts with no end date")
        void shouldSkipContractsWithoutEndDate() {
            Users user = adminUser("token-3");
            when(usersRepository.findByCalendarToken("token-3")).thenReturn(Optional.of(user));
            when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG_ID))
                    .thenReturn(List.of(contract(3L, "CTR-003", "Gamma", null)));

            String ics = calendarFeedService.buildIcsFeed("token-3");

            assertTrue(ics.contains("BEGIN:VCALENDAR"));
            assertEquals(-1, ics.indexOf("BEGIN:VEVENT"));
        }

        @Test
        @DisplayName("escapes commas, semicolons and backslashes in SUMMARY per RFC 5545")
        void shouldEscapeSpecialCharacters() {
            Users user = adminUser("token-4");
            when(usersRepository.findByCalendarToken("token-4")).thenReturn(Optional.of(user));
            when(contractsRepository.findByStatusAndOrganization_Id(ContractStatus.ACTIVE, ORG_ID))
                    .thenReturn(List.of(
                            contract(4L, "CTR-004", "Cliente; Sub, Ramo\\Test", LocalDate.of(2026, Month.OCTOBER, 10))));

            String ics = calendarFeedService.buildIcsFeed("token-4");

            assertTrue(ics.contains("SUMMARY:Scadenza contratto CTR-004 - Cliente\\; Sub\\, Ramo\\\\Test\r\n"));
        }

        @Test
        @DisplayName("manager with no assigned Manager profile gets an empty (but valid) calendar")
        void shouldReturnEmptyCalendarWhenManagerHasNoProfile() {
            Roles managerRole = Roles.builder().id(2L).role("MANAGER").build();
            Users user = Users.builder().id(3L).username("orphan").role(managerRole).calendarToken("token-5").build();
            when(usersRepository.findByCalendarToken("token-5")).thenReturn(Optional.of(user));

            String ics = calendarFeedService.buildIcsFeed("token-5");

            assertEquals(-1, ics.indexOf("BEGIN:VEVENT"));
            assertTrue(ics.contains("BEGIN:VCALENDAR"));
        }
    }
}

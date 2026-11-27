package com.analoghub.platform.filmlab;

import com.analoghub.platform.common.exception.BusinessException;
import com.analoghub.platform.common.exception.ErrorCode;
import com.analoghub.platform.filmlab.domain.Lab;
import com.analoghub.platform.filmlab.domain.LabCapacityRule;
import com.analoghub.platform.filmlab.domain.LabMember;
import com.analoghub.platform.filmlab.event.FilmLabCreatedEvent;
import com.analoghub.platform.filmlab.facade.FilmLabFacade;
import com.analoghub.platform.filmlab.facade.FilmLabFacadeImpl;
import com.analoghub.platform.filmlab.repository.LabCapacityRuleRepository;
import com.analoghub.platform.filmlab.repository.LabMemberRepository;
import com.analoghub.platform.filmlab.repository.LabRepository;
import com.analoghub.platform.filmlab.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilmLabProvisioningFacadeTest {

    @Mock
    private LabCapacityRuleRepository capacityRuleRepository;

    @Mock
    private ServicePackageRepository servicePackageRepository;

    @Mock
    private LabRepository labRepository;

    @Mock
    private LabMemberRepository labMemberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FilmLabFacadeImpl filmLabFacade;

    @BeforeEach
    void setUp() {
        filmLabFacade = new FilmLabFacadeImpl(
                capacityRuleRepository,
                servicePackageRepository,
                labRepository,
                labMemberRepository,
                eventPublisher
        );
    }

    @Test
    @DisplayName("provisionLabFromApprovedApplication creates ACTIVE Lab, OWNER LabMember, normalized capacity rules and emits FilmLabCreatedEvent")
    void shouldProvisionActiveLabWithOwnerMemberAndNormalizedProcesses() {
        UUID ownerId = UUID.randomUUID();
        UUID createdLabId = UUID.randomUUID();

        Map<String, Object> payload = Map.of(
                "proposedLabName", "Sài Gòn Vintage Darkroom Lab",
                "fullAddress", "45A Lý Tự Trọng, Quận 1, TP.HCM",
                "districtCode", "760",
                "provinceCode", "79",
                "latitude", 10.7785,
                "longitude", 106.7012,
                "supportedProcesses", List.of("C-41", "B&W", "ECN-2", "UNKNOWN_PROCESS")
        );

        when(labRepository.findBySlug("sai-gon-vintage-darkroom-lab")).thenReturn(Optional.empty());
        when(labRepository.findFirstByOwnerUserIdOrderByCreatedAtDesc(ownerId)).thenReturn(Optional.empty());
        when(labRepository.save(any(Lab.class))).thenAnswer(inv -> {
            Lab lab = inv.getArgument(0);
            if (lab.getId() == null) {
                lab.setId(createdLabId);
            }
            return lab;
        });
        when(labMemberRepository.findByLabIdAndUserId(createdLabId, ownerId)).thenReturn(Optional.empty());
        when(labMemberRepository.save(any(LabMember.class))).thenAnswer(inv -> inv.getArgument(0));
        when(capacityRuleRepository.findByLabIdAndProcessType(eq(createdLabId), any())).thenReturn(Optional.empty());

        FilmLabFacade.ProvisionedLabInfo info = filmLabFacade.provisionLabFromApprovedApplication(ownerId, payload);

        assertThat(info.labId()).isEqualTo(createdLabId);
        assertThat(info.ownerUserId()).isEqualTo(ownerId);
        assertThat(info.slug()).isEqualTo("sai-gon-vintage-darkroom-lab");
        assertThat(info.name()).isEqualTo("Sài Gòn Vintage Darkroom Lab");
        assertThat(info.status()).isEqualTo("ACTIVE");

        ArgumentCaptor<Lab> labCaptor = ArgumentCaptor.forClass(Lab.class);
        verify(labRepository).save(labCaptor.capture());
        assertThat(labCaptor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(labCaptor.getValue().getOwnerUserId()).isEqualTo(ownerId);
        assertThat(labCaptor.getValue().getDistrictCode()).isEqualTo("760");
        assertThat(labCaptor.getValue().getProvinceCode()).isEqualTo("79");

        ArgumentCaptor<LabMember> memberCaptor = ArgumentCaptor.forClass(LabMember.class);
        verify(labMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getLabId()).isEqualTo(createdLabId);
        assertThat(memberCaptor.getValue().getUserId()).isEqualTo(ownerId);
        assertThat(memberCaptor.getValue().getMemberRole()).isEqualTo("OWNER");
        assertThat(memberCaptor.getValue().isActive()).isTrue();

        ArgumentCaptor<LabCapacityRule> capacityCaptor = ArgumentCaptor.forClass(LabCapacityRule.class);
        verify(capacityRuleRepository, atLeastOnce()).save(capacityCaptor.capture());
        assertThat(capacityCaptor.getAllValues())
                .extracting(LabCapacityRule::getProcessType)
                .containsExactlyInAnyOrder("C41", "BW", "ECN2");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(FilmLabCreatedEvent.class);
        FilmLabCreatedEvent createdEvent = (FilmLabCreatedEvent) eventCaptor.getValue();
        assertThat(createdEvent.labId()).isEqualTo(createdLabId);
        assertThat(createdEvent.labName()).isEqualTo("Sài Gòn Vintage Darkroom Lab");
    }

    @Test
    @DisplayName("provisionLabFromApprovedApplication updates existing PENDING_APPROVAL Lab owned by same user to ACTIVE")
    void shouldUpdateExistingPendingLabOwnedBySameUserToActive() {
        UUID ownerId = UUID.randomUUID();
        UUID existingLabId = UUID.randomUUID();

        Lab existingLab = Lab.builder()
                .id(existingLabId)
                .ownerUserId(ownerId)
                .slug("old-slug")
                .name("Old Lab")
                .fullAddress("Old Address")
                .districtCode("760")
                .provinceCode("79")
                .status("PENDING_APPROVAL")
                .build();

        when(labRepository.findBySlug("updated-lab")).thenReturn(Optional.empty());
        when(labRepository.findFirstByOwnerUserIdOrderByCreatedAtDesc(ownerId)).thenReturn(Optional.of(existingLab));
        when(labRepository.save(any(Lab.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labMemberRepository.findByLabIdAndUserId(existingLabId, ownerId)).thenReturn(Optional.empty());
        when(capacityRuleRepository.findByLabIdAndProcessType(eq(existingLabId), any())).thenReturn(Optional.empty());

        FilmLabFacade.ProvisionedLabInfo info = filmLabFacade.provisionLabFromApprovedApplication(
                ownerId,
                Map.of(
                        "proposedLabName", "Updated Lab",
                        "proposedSlug", "updated-lab",
                        "labAddress", "12 Nguyễn Trãi, Quận 5"
                )
        );

        assertThat(info.labId()).isEqualTo(existingLabId);
        assertThat(info.slug()).isEqualTo("updated-lab");
        assertThat(info.status()).isEqualTo("ACTIVE");
        assertThat(existingLab.getFullAddress()).isEqualTo("12 Nguyễn Trãi, Quận 5");
    }

    @Test
    @DisplayName("provisionLabFromApprovedApplication throws LAB_SLUG_ALREADY_EXISTS when slug belongs to another active user")
    void shouldRejectDuplicateSlugOwnedByAnotherUser() {
        UUID applicantId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();
        String slug = "saigon-darkroom";

        when(labRepository.findBySlug(slug)).thenReturn(Optional.of(
                Lab.builder()
                        .id(UUID.randomUUID())
                        .ownerUserId(otherOwnerId)
                        .slug(slug)
                        .name("Saigon Darkroom")
                        .fullAddress("123 Nguyễn Huệ")
                        .districtCode("760")
                        .provinceCode("79")
                        .status("ACTIVE")
                        .build()
        ));

        assertThatThrownBy(() -> filmLabFacade.provisionLabFromApprovedApplication(
                applicantId,
                Map.of(
                        "proposedLabName", "Saigon Darkroom",
                        "proposedSlug", slug,
                        "fullAddress", "45A Lý Tự Trọng"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.LAB_SLUG_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("approveLabOpening transitions PENDING_APPROVAL Lab to ACTIVE, creates OWNER LabMember and default capacity rules")
    void shouldApproveLabOpeningAndEnsureOwnerAndDefaultRules() {
        UUID labId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Lab pendingLab = Lab.builder()
                .id(labId)
                .ownerUserId(ownerId)
                .slug("hanoi-analog-lab")
                .name("Hanoi Analog Lab")
                .fullAddress("12 Tràng Tiền, Hoàn Kiếm, Hà Nội")
                .districtCode("002")
                .provinceCode("01")
                .status("PENDING_APPROVAL")
                .build();

        when(labRepository.findById(labId)).thenReturn(Optional.of(pendingLab));
        when(labRepository.save(any(Lab.class))).thenAnswer(inv -> inv.getArgument(0));
        when(labMemberRepository.findByLabIdAndUserId(labId, ownerId)).thenReturn(Optional.empty());
        when(capacityRuleRepository.findByLabIdAndProcessType(eq(labId), any())).thenReturn(Optional.empty());

        FilmLabFacade.ProvisionedLabInfo info = filmLabFacade.approveLabOpening(labId);

        assertThat(info.labId()).isEqualTo(labId);
        assertThat(info.ownerUserId()).isEqualTo(ownerId);
        assertThat(info.status()).isEqualTo("ACTIVE");
        assertThat(pendingLab.getStatus()).isEqualTo("ACTIVE");

        verify(labMemberRepository).save(any(LabMember.class));
        verify(capacityRuleRepository, atLeastOnce()).save(any(LabCapacityRule.class));
        verify(eventPublisher).publishEvent(any(FilmLabCreatedEvent.class));
    }

    @Test
    @DisplayName("approveLabOpening throws DUPLICATE_RESOURCE when Lab is already ACTIVE")
    void shouldRejectApproveLabOpeningWhenAlreadyActive() {
        UUID labId = UUID.randomUUID();
        Lab activeLab = Lab.builder()
                .id(labId)
                .ownerUserId(UUID.randomUUID())
                .slug("active-lab")
                .name("Active Lab")
                .fullAddress("123 Lê Lợi")
                .districtCode("760")
                .provinceCode("79")
                .status("ACTIVE")
                .build();

        when(labRepository.findById(labId)).thenReturn(Optional.of(activeLab));

        assertThatThrownBy(() -> filmLabFacade.approveLabOpening(labId))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("getLabsByStatus defaults blank status to PENDING_APPROVAL and maps ProvisionedLabInfo")
    void shouldListLabsByStatus() {
        UUID labId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Lab pendingLab = Lab.builder()
                .id(labId)
                .ownerUserId(ownerId)
                .slug("pending-lab")
                .name("Pending Lab")
                .fullAddress("123 Pasteur")
                .districtCode("760")
                .provinceCode("79")
                .status("PENDING_APPROVAL")
                .build();

        when(labRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc("PENDING_APPROVAL"))
                .thenReturn(List.of(pendingLab));

        List<FilmLabFacade.ProvisionedLabInfo> results = filmLabFacade.getLabsByStatus(null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().labId()).isEqualTo(labId);
        assertThat(results.getFirst().status()).isEqualTo("PENDING_APPROVAL");
    }
}

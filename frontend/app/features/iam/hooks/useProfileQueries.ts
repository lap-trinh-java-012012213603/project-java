import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/shared/api/queryKeys";
import { iamApi, syncProfileToAuthStore } from "../services/iamApi";
import type {
  DeleteUserAddressResponse,
  PresignedAvatarUploadResponse,
  RoleApplicationDto,
  RoleApplicationStatus,
  SubmitRoleApplicationRequest,
  UpdateUserProfileRequest,
  UpsertUserAddressRequest,
  UserAddressDto,
  UserProfileDto,
} from "../types/iam.types";

export function useMyProfileQuery() {
  return useQuery<UserProfileDto>({
    queryKey: queryKeys.iam.profile(),
    queryFn: () => iamApi.getMyProfile(),
  });
}

export function useUploadAvatarMutation(
  onSuccessCallback?: (updated: UserProfileDto) => void
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (file: File): Promise<UserProfileDto> => iamApi.uploadMyAvatar(file),
    onSuccess: (updated) => {
      syncProfileToAuthStore(updated);
      queryClient.setQueryData(queryKeys.iam.profile(), updated);
      onSuccessCallback?.(updated);
    },
  });
}

export function useUpdateProfileMutation(
  onSuccessCallback?: (updated: UserProfileDto) => void
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpdateUserProfileRequest) => iamApi.updateMyProfile(payload),
    onSuccess: (updated) => {
      syncProfileToAuthStore(updated);
      queryClient.setQueryData(queryKeys.iam.profile(), updated);
      onSuccessCallback?.(updated);
    },
  });
}

export function useMyAddressesQuery() {
  return useQuery<UserAddressDto[]>({
    queryKey: queryKeys.iam.addresses(),
    queryFn: () => iamApi.getMyAddresses(),
  });
}

export function useCreateAddressMutation(
  onSuccessCallback?: (created: UserAddressDto) => void
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: UpsertUserAddressRequest) => iamApi.createAddress(payload),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.iam.addresses() });
      onSuccessCallback?.(created);
    },
  });
}

export function useUpdateAddressMutation(
  onSuccessCallback?: (updated: UserAddressDto) => void
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpsertUserAddressRequest }) =>
      iamApi.updateAddress(id, payload),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.iam.addresses() });
      onSuccessCallback?.(updated);
    },
  });
}

export function useDeleteAddressMutation(
  onSuccessCallback?: (res: DeleteUserAddressResponse) => void
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => iamApi.deleteAddress(id),
    onSuccess: (res) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.iam.addresses() });
      onSuccessCallback?.(res);
    },
  });
}

export function useMyRoleApplicationsQuery(status?: RoleApplicationStatus) {
  return useQuery<RoleApplicationDto[]>({
    queryKey: queryKeys.iam.roleApplications(status),
    queryFn: () => iamApi.getMyRoleApplications(status),
  });
}

export function useSubmitRoleApplicationMutation(
  onSuccessCallback?: (submitted: RoleApplicationDto) => void
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: SubmitRoleApplicationRequest) =>
      iamApi.submitRoleApplication(payload),
    onSuccess: (submitted) => {
      queryClient.invalidateQueries({ queryKey: queryKeys.iam.all });
      onSuccessCallback?.(submitted);
    },
  });
}

import { useMutation } from "@tanstack/react-query";
import { useAuthStore } from "@/shared/stores/useAuthStore";
import { iamApi, persistAuthSession } from "../services/iamApi";
import type {
  AuthTokenResponse,
  GoogleOAuth2LoginRequest,
  LoginRequest,
  RegisterUserRequest,
} from "../types/iam.types";

export function useLoginMutation(onSuccessCallback?: (response: AuthTokenResponse) => void) {
  return useMutation({
    mutationFn: (payload: LoginRequest) => iamApi.login(payload),
    onSuccess: (response) => {
      persistAuthSession(response);
      onSuccessCallback?.(response);
    },
  });
}

export function useRegisterMutation(onSuccessCallback?: (response: AuthTokenResponse) => void) {
  return useMutation({
    mutationFn: (payload: RegisterUserRequest) => iamApi.register(payload),
    onSuccess: (response) => {
      persistAuthSession(response);
      onSuccessCallback?.(response);
    },
  });
}

export function useGoogleLoginMutation(onSuccessCallback?: (response: AuthTokenResponse) => void) {
  return useMutation({
    mutationFn: (payload: GoogleOAuth2LoginRequest) => iamApi.loginWithGoogle(payload),
    onSuccess: (response) => {
      persistAuthSession(response);
      onSuccessCallback?.(response);
    },
  });
}

export function useLogoutMutation(onSettledCallback?: () => void) {
  return useMutation({
    mutationFn: async () => {
      const refreshToken = useAuthStore.getState().refreshToken;
      return iamApi.logout({ refreshToken });
    },
    onSettled: () => {
      useAuthStore.getState().logout();
      onSettledCallback?.();
    },
  });
}

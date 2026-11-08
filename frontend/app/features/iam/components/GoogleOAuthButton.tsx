import React, { useEffect, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
import { Button } from "@/shared/components/ui/Button";
import { parseApiError } from "@/shared/api/handleApiError";
import { generateUuidV7 } from "@/shared/utils/uuid";
import { useGoogleLoginMutation } from "../hooks/useAuthMutations";
import { getBrowserDeviceInfo, resolvePostAuthRedirect } from "../utils/authRedirect";

interface GoogleCredentialResponse {
  credential?: string;
}

interface GoogleAccountsId {
  initialize: (config: {
    client_id: string;
    callback: (response: GoogleCredentialResponse) => void;
  }) => void;
  prompt: () => void;
}

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: GoogleAccountsId;
      };
    };
  }
}

export interface GoogleOAuthButtonProps {
  label?: string;
  onErrorMessage?: (message: string) => void;
}

function isConfiguredClientId(clientId: string | undefined): clientId is string {
  return Boolean(
    clientId &&
      clientId.trim().length > 0 &&
      !clientId.includes("your-google-client-id")
  );
}

export const GoogleOAuthButton: React.FC<GoogleOAuthButtonProps> = ({
  label = "Tiếp tục với Google",
  onErrorMessage,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const [localError, setLocalError] = useState<string | null>(null);

  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
  const redirectTarget = resolvePostAuthRedirect(searchParams, location.state);

  const googleMutation = useGoogleLoginMutation(() => {
    navigate(redirectTarget, { replace: true });
  });

  useEffect(() => {
    if (typeof window === "undefined" || !isConfiguredClientId(clientId)) {
      return;
    }

    const handleCredential = (response: GoogleCredentialResponse) => {
      if (!response.credential) {
        const msg = "Không nhận được Google ID Token hợp lệ.";
        setLocalError(msg);
        onErrorMessage?.(msg);
        return;
      }
      googleMutation.mutate(
        {
          idToken: response.credential,
          deviceInfo: getBrowserDeviceInfo(),
        },
        {
          onError: (err) => {
            const parsed = parseApiError(err);
            setLocalError(parsed);
            onErrorMessage?.(parsed);
          },
        }
      );
    };

    if (window.google?.accounts?.id) {
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: handleCredential,
      });
      return;
    }

    const existingScript = document.querySelector<HTMLScriptElement>(
      'script[src="https://accounts.google.com/gsi/client"]'
    );
    if (existingScript) return;

    const script = document.createElement("script");
    script.src = "https://accounts.google.com/gsi/client";
    script.async = true;
    script.defer = true;
    script.onload = () => {
      window.google?.accounts?.id?.initialize({
        client_id: clientId,
        callback: handleCredential,
      });
    };
    document.head.appendChild(script);
  }, [clientId]);

  const handleGoogleSignInClick = () => {
    setLocalError(null);

    if (!isConfiguredClientId(clientId)) {
      const msg = "Chưa cấu hình VITE_GOOGLE_CLIENT_ID cho đăng nhập Google OAuth2.";
      setLocalError(msg);
      onErrorMessage?.(msg);
      return;
    }

    const callbackUri = `${window.location.origin}/auth/oauth2/callback`;
    const nonce = generateUuidV7();
    const oauthParams = new URLSearchParams({
      client_id: clientId,
      redirect_uri: callbackUri,
      response_type: "id_token",
      scope: "openid email profile",
      nonce,
      state: redirectTarget,
      prompt: "select_account",
    });

    window.location.assign(`https://accounts.google.com/o/oauth2/v2/auth?${oauthParams.toString()}`);
  };

  return (
    <div className="space-y-2">
      <Button
        type="button"
        variant="secondary"
        className="w-full font-medium"
        isLoading={googleMutation.isPending}
        onClick={handleGoogleSignInClick}
        leftIcon={
          <svg className="w-4 h-4" viewBox="0 0 24 24" aria-hidden="true">
            <path
              fill="#EA4335"
              d="M12 10.2v3.9h5.5c-.2 1.3-1.5 3.9-5.5 3.9-3.3 0-6-2.7-6-6s2.7-6 6-6c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.8 3.4 14.6 2.5 12 2.5 6.8 2.5 2.5 6.8 2.5 12s4.3 9.5 9.5 9.5c5.5 0 9.1-3.9 9.1-9.3 0-.6-.1-1.1-.2-1.6H12z"
            />
          </svg>
        }
      >
        {label}
      </Button>
      {localError && !onErrorMessage && (
        <p className="text-xs text-safelight-red text-center">{localError}</p>
      )}
    </div>
  );
};

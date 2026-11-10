import React, { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";
import { useLocation, useNavigate, useSearchParams } from "react-router";
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
      <button
        type="button"
        disabled={googleMutation.isPending}
        onClick={handleGoogleSignInClick}
        className="w-full h-12 px-4 bg-white border border-stone-200 hover:border-stone-300 hover:bg-stone-50 transition-all rounded-xl font-semibold text-sm text-stone-800 flex items-center justify-center gap-3 shadow-xs active:scale-[0.99] cursor-pointer disabled:opacity-60"
      >
        {googleMutation.isPending ? (
          <Loader2 className="w-5 h-5 animate-spin text-stone-500" />
        ) : (
          <svg className="w-5 h-5 shrink-0" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
              fill="#4285F4"
            />
            <path
              d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
              fill="#34A853"
            />
            <path
              d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z"
              fill="#FBBC05"
            />
            <path
              d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z"
              fill="#EA4335"
            />
          </svg>
        )}
        <span>{label}</span>
      </button>
      {localError && !onErrorMessage && (
        <p className="text-xs text-[#E11D48] text-center">{localError}</p>
      )}
    </div>
  );
};

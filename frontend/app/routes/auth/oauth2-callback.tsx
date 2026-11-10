import { useEffect, useRef, useState } from "react";
import { AlertTriangle, Loader2 } from "lucide-react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router";
import { useGoogleLoginMutation } from "@/features/iam/hooks/useAuthMutations";
import {
  getBrowserDeviceInfo,
  resolvePostAuthRedirect,
} from "@/features/iam/utils/authRedirect";
import { parseApiError } from "@/shared/api/handleApiError";
import { Button } from "@/shared/components/ui/Button";

export function meta() {
  return [{ title: "Xác Thực Google OAuth2 — AnalogHub" }];
}

export default function OAuth2CallbackPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const hasTriggeredRef = useRef(false);
  const [callbackError, setCallbackError] = useState<string | null>(null);

  const hashParams = new URLSearchParams(
    location.hash.startsWith("#") ? location.hash.slice(1) : location.hash
  );

  const mergedRedirectParams = new URLSearchParams(searchParams);
  const hashState = hashParams.get("state") || hashParams.get("redirect");
  if (hashState && !mergedRedirectParams.get("redirect") && !mergedRedirectParams.get("state")) {
    mergedRedirectParams.set("state", hashState);
  }

  const redirectTarget = resolvePostAuthRedirect(mergedRedirectParams, location.state);

  const googleMutation = useGoogleLoginMutation(() => {
    navigate(redirectTarget, { replace: true });
  });

  useEffect(() => {
    if (hasTriggeredRef.current) return;
    hasTriggeredRef.current = true;

    const oauthError = hashParams.get("error") || searchParams.get("error");
    if (oauthError) {
      setCallbackError("Phiên xác thực Google đã bị hủy hoặc từ chối quyền truy cập.");
      return;
    }

    const idToken =
      hashParams.get("id_token") ||
      hashParams.get("credential") ||
      searchParams.get("id_token") ||
      searchParams.get("credential");

    if (!idToken) {
      setCallbackError("Không tìm thấy Google ID Token hợp lệ trong phản hồi xác thực.");
      return;
    }

    googleMutation.mutate(
      {
        idToken,
        deviceInfo: getBrowserDeviceInfo(),
      },
      {
        onError: (err) => {
          setCallbackError(parseApiError(err));
        },
      }
    );
  }, []);

  if (callbackError) {
    return (
      <div className="w-full max-w-md bg-white border border-[#E6E4E0] rounded-2xl shadow-lg p-8 space-y-5 text-center">
        <div className="w-12 h-12 mx-auto rounded-full bg-[#FFF1F2] border border-[#FECDD3] flex items-center justify-center text-[#E11D48]">
          <AlertTriangle className="w-6 h-6" />
        </div>
        <div className="space-y-1.5">
          <h1 className="text-lg font-extrabold text-[#111111]">Xác Thực Google Thất Bại</h1>
          <p className="text-xs text-stone-500">{callbackError}</p>
        </div>
        <Link to="/auth/login" className="block">
          <Button variant="primary" className="w-full">
            Quay lại màn hình đăng nhập
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="w-full max-w-md bg-white border border-[#E6E4E0] rounded-2xl shadow-lg p-8 flex flex-col items-center justify-center text-center space-y-4">
      <Loader2 className="w-8 h-8 text-[#D97706] animate-spin" />
      <div className="space-y-1">
        <h1 className="text-base font-bold text-[#111111]">
          Đang xác thực tài khoản Google...
        </h1>
        <p className="text-xs text-stone-500">
          Vui lòng đợi giây lát trong khi hệ thống khởi tạo phiên làm việc bảo mật.
        </p>
      </div>
    </div>
  );
}

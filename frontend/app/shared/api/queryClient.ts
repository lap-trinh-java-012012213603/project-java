import { QueryClient } from "@tanstack/react-query";

interface ErrorWithResponseStatus {
  response?: {
    status?: number;
  };
}

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60, // 1 phút dữ liệu coi là fresh
        gcTime: 1000 * 60 * 10, // 10 phút lưu trữ trong garbage collector
        retry: (failureCount, error: unknown) => {
          // Không retry đối với các lỗi 401, 403, 404
          const status = (error as ErrorWithResponseStatus | null)?.response?.status;
          if (status === 401 || status === 403 || status === 404) {
            return false;
          }
          return failureCount < 2;
        },
        refetchOnWindowFocus: false, // Tránh gọi lại API gây giật lag UI khi đổi tab
        refetchOnReconnect: true, // Tự động refetch khi có mạng trở lại
      },
      mutations: {
        retry: 0, // Không tự động retry mutation tránh lặp giao dịch
      },
    },
  });
}

export const queryClient = createQueryClient();

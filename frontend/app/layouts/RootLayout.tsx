import React, { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { Outlet } from "react-router";
import { createQueryClient, queryClient } from "@/shared/api/queryClient";

export interface RootLayoutProps {
  children?: React.ReactNode;
  client?: QueryClient;
}

export const RootLayout: React.FC<RootLayoutProps> = ({ children, client }) => {
  const [activeQueryClient] = useState<QueryClient>(
    () => client ?? (typeof window === "undefined" ? createQueryClient() : queryClient)
  );

  return (
    <QueryClientProvider client={activeQueryClient}>
      {children ?? <Outlet />}
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
};

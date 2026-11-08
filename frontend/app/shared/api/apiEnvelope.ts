export interface ApiMeta {
  timestamp?: string;
  traceId?: string;
}

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp?: string;
  meta?: ApiMeta;
}

export interface ApiFieldError {
  field: string;
  rejectedValue?: unknown;
  message: string;
}

export interface ApiErrorResponse {
  success: false;
  code?: string;
  errorCode?: string;
  message: string;
  path?: string;
  timestamp?: string;
  traceId?: string;
  fieldErrors?: ApiFieldError[] | null;
}

export interface PageResponse<T> {
  items?: T[];
  item?: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
}

export interface CursorPageResponse<T> {
  items: T[];
  nextCursor: string | null;
  hasMore: boolean;
  limit: number;
}

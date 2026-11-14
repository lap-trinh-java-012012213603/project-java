export const queryKeys = {
  // 1. Phân hệ Khách hàng & Lab
  labs: {
    all: ["labs"] as const,
    lists: () => [...queryKeys.labs.all, "list"] as const,
    list: (params: Record<string, unknown>) => [...queryKeys.labs.lists(), params] as const,
    details: () => [...queryKeys.labs.all, "detail"] as const,
    detail: (slugOrId: string) => [...queryKeys.labs.details(), slugOrId] as const,
    reviews: (labId: string) => [...queryKeys.labs.detail(labId), "reviews"] as const,
    packages: (labId: string) => [...queryKeys.labs.detail(labId), "packages"] as const,
  },

  // 2. Phân hệ Vận hành Phòng Lab (Kanban & Workflow)
  kanban: {
    all: ["kanban"] as const,
    board: (labId: string) => [...queryKeys.kanban.all, "board", labId] as const,
    roll: (rollId: string) => [...queryKeys.kanban.all, "roll", rollId] as const,
  },

  // 3. Phân hệ Kho Lưu trữ Số (Digital Archive)
  archive: {
    all: ["archive"] as const,
    albums: () => [...queryKeys.archive.all, "albums"] as const,
    album: (albumId: string) => [...queryKeys.archive.albums(), albumId] as const,
    rolls: () => [...queryKeys.archive.all, "rolls"] as const,
    roll: (rollId: string) => [...queryKeys.archive.rolls(), rollId] as const,
    frame: (frameId: string) => [...queryKeys.archive.all, "frame", frameId] as const,
  },

  // 4. Phân hệ Chợ C2C Máy ảnh & Escrow
  marketplace: {
    all: ["marketplace"] as const,
    listings: (filters?: Record<string, unknown>) =>
      [...queryKeys.marketplace.all, "listings", filters] as const,
    listing: (id: string) => [...queryKeys.marketplace.all, "listing", id] as const,
    offers: (listingId: string) => [...queryKeys.marketplace.listing(listingId), "offers"] as const,
    orders: () => [...queryKeys.marketplace.all, "orders"] as const,
    order: (orderId: string) => [...queryKeys.marketplace.orders(), orderId] as const,
  },

  // 5. Phân hệ Tin nhắn & Hội thoại
  messaging: {
    all: ["messaging"] as const,
    conversations: () => [...queryKeys.messaging.all, "conversations"] as const,
    messages: (conversationId: string) =>
      [...queryKeys.messaging.all, "messages", conversationId] as const,
  },

  // 6. Phân hệ Quản trị & Điều phối Admin
  admin: {
    all: ["admin"] as const,
    disputes: (status?: string) => [...queryKeys.admin.all, "disputes", status] as const,
    dispute: (id: string) => [...queryKeys.admin.all, "dispute", id] as const,
    approvals: (type?: string) => [...queryKeys.admin.all, "approvals", type] as const,
    payouts: () => [...queryKeys.admin.all, "payouts"] as const,
  },
};

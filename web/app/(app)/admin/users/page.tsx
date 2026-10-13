"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { api } from "@/lib/api/client";
import { errorMessage, unwrap } from "@/lib/api/errors";
import { ROLE_LABEL, type Role } from "@/lib/auth/roles";
import { formatDateTime } from "@/lib/format";
import { Badge, Button, EmptyState, ErrorState, PageLoading, inputBaseClass } from "@/components/ui";

const PAGE_SIZE = 50;

/** Read-only user directory. */
export default function AdminUsersPage() {
  const [role, setRole] = useState<Role | "">("");
  const [page, setPage] = useState(0);
  const users = useQuery({
    queryKey: ["admin", "users", role, page],
    queryFn: () => unwrap(api.GET("/api/admin/users", { params: { query: { role: role || undefined, page, size: PAGE_SIZE } } })),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="mr-auto text-2xl font-bold">Users</h1>
        <select
          className={`${inputBaseClass} w-48`}
          aria-label="Filter by role"
          value={role}
          onChange={(e) => {
            setRole(e.target.value as Role | "");
            setPage(0);
          }}
        >
          <option value="">All roles</option>
          {(Object.keys(ROLE_LABEL) as Role[]).map((r) => (
            <option key={r} value={r}>
              {ROLE_LABEL[r]}
            </option>
          ))}
        </select>
      </div>
      {users.isPending ? (
        <PageLoading />
      ) : users.isError ? (
        <ErrorState message={errorMessage(users.error)} onRetry={() => users.refetch()} />
      ) : users.data.items.length === 0 ? (
        <EmptyState title="No users" />
      ) : (
        <>
          <div className="overflow-x-auto rounded-2xl border border-stone-200 bg-white">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead className="bg-stone-50 text-stone-600">
                <tr>
                  <th className="px-3 py-2 font-medium">Name</th>
                  <th className="px-3 py-2 font-medium">Phone</th>
                  <th className="px-3 py-2 font-medium">Email</th>
                  <th className="px-3 py-2 font-medium">Role</th>
                  <th className="px-3 py-2 font-medium">Status</th>
                  <th className="px-3 py-2 font-medium">Joined</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-stone-100">
                {users.data.items.map((u) => (
                  <tr key={u.id}>
                    <td className="px-3 py-2">{u.name}</td>
                    <td className="px-3 py-2 font-mono text-xs">{u.phone}</td>
                    <td className="px-3 py-2">{u.email ?? "—"}</td>
                    <td className="px-3 py-2">{ROLE_LABEL[u.role]}</td>
                    <td className="px-3 py-2">
                      <Badge tone={u.status === "ACTIVE" ? "green" : u.status === "DISABLED" ? "red" : "amber"}>{u.status.replace("_", " ").toLowerCase()}</Badge>
                    </td>
                    <td className="px-3 py-2 text-stone-500">{formatDateTime(u.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex items-center justify-between">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <span className="text-sm text-stone-500">
              {users.data.totalItems} users · page {page + 1} of {Math.max(1, users.data.totalPages)}
            </span>
            <Button variant="secondary" disabled={page + 1 >= users.data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

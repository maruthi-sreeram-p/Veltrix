import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { api, queryString, tokenStore } from './api';
import { exportFileName } from './privacy';
import type {
  AdapterDefinition,
  AdapterInfo,
  CandidateProfile,
  ConsentOverview,
  ConsentPurpose,
  ErasureView,
  ResumeSummary,
  DashboardResponse,
  CompanyDiscoveryResult,
  JobDetail,
  JobPage,
  JobSummary,
  NotificationView,
  Page,
  PreferencesPayload,
  ResumeParseResult,
  ProposalView,
  ReviewResult,
  Decision,
  SourceDetail,
  SourceStats,
  SourceSummary,
  SystemStats,
} from './types';

/** Query keys in one place so invalidation cannot drift from subscription. */
export const keys = {
  dashboard: ['dashboard'] as const,
  profile: ['candidate', 'profile'] as const,
  jobs: (params: unknown) => ['jobs', params] as const,
  job: (id: string) => ['job', id] as const,
  recommended: ['jobs', 'recommended'] as const,
  saved: ['jobs', 'saved'] as const,
  applied: ['jobs', 'applied'] as const,
  sources: (params: unknown) => ['sources', params] as const,
  source: (id: string) => ['source', id] as const,
  sourceStats: ['sources', 'stats'] as const,
  adapters: ['sources', 'adapters'] as const,
  notifications: ['notifications'] as const,
  unreadCount: ['notifications', 'unread'] as const,
  systemStats: ['admin', 'stats'] as const,
  pendingProposal: ['candidate', 'resume-proposal', 'pending'] as const,
  resumes: ['candidate', 'resumes'] as const,
  consents: ['candidate', 'consents'] as const,
  erasure: ['candidate', 'erasure'] as const,
};

/* ------------------------------------------------------------- Dashboard */

export function useDashboard() {
  return useQuery({
    queryKey: keys.dashboard,
    queryFn: ({ signal }) => api.get<DashboardResponse>('/api/dashboard', signal),
  });
}

/* ------------------------------------------------------------- Candidate */

export function useProfile() {
  return useQuery({
    queryKey: keys.profile,
    queryFn: ({ signal }) => api.get<CandidateProfile>('/api/candidate/profile', signal),
  });
}

export function useUploadResume() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append('file', file);
      return api.upload<ResumeParseResult>('/api/candidate/resume', form);
    },
    onSuccess: (result) => {
      queryClient.setQueryData(keys.profile, result.profile);
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
      // The upload did not change the profile. What it produced is a reading
      // waiting for the student, so the review panel needs to refetch.
      queryClient.invalidateQueries({ queryKey: keys.pendingProposal });
    },
  });
}

/* --------------------------------------------------- AI profile proposals */

/** The reading still waiting for this student, if any. 204 arrives as undefined. */
export function usePendingProposal() {
  return useQuery({
    queryKey: keys.pendingProposal,
    queryFn: ({ signal }) =>
      api.get<ProposalView | undefined>('/api/candidate/resume-proposals/pending', signal),
  });
}

export function useApproveProposal(proposalId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (decisions: Decision[]) =>
      api.post<ReviewResult>(
        `/api/candidate/resume-proposals/${proposalId}/approve`,
        { decisions },
      ),
    onSuccess: (result) => {
      queryClient.setQueryData(keys.profile, result.profile);
      queryClient.setQueryData(keys.pendingProposal, undefined);
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

export function useRejectProposal(proposalId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api.post<ReviewResult>(`/api/candidate/resume-proposals/${proposalId}/reject`),
    onSuccess: (result) => {
      queryClient.setQueryData(keys.profile, result.profile);
      queryClient.setQueryData(keys.pendingProposal, undefined);
    },
  });
}

export function useSaveProfile() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      api.put<CandidateProfile>('/api/candidate/profile', body),
    onSuccess: (profile) => {
      queryClient.setQueryData(keys.profile, profile);
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

export function useSavePreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: PreferencesPayload) =>
      api.put<CandidateProfile>('/api/candidate/preferences', body),
    onSuccess: (profile) => {
      queryClient.setQueryData(keys.profile, profile);
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

/* ------------------------------------------------------------------ Jobs */

export interface JobQueryParams {
  q?: string;
  location?: string;
  workMode?: string[];
  employmentType?: string[];
  seniority?: string[];
  maxExperience?: number;
  postedWithinDays?: number;
  sourceId?: string;
  skill?: string;
  minMatch?: number;
  includeClosed?: boolean;
  page?: number;
  size?: number;
  sort?: string;
}

export function useJobs(params: JobQueryParams) {
  return useQuery({
    queryKey: keys.jobs(params),
    queryFn: ({ signal }) =>
      api.get<JobPage>(`/api/jobs${queryString(params as Record<string, unknown>)}`, signal),
    placeholderData: (previous) => previous,
  });
}

export function useJob(jobId: string | undefined) {
  return useQuery({
    queryKey: keys.job(jobId ?? ''),
    queryFn: ({ signal }) => api.get<JobDetail>(`/api/jobs/${jobId}`, signal),
    enabled: Boolean(jobId),
  });
}

export function useSavedJobs() {
  return useQuery({
    queryKey: keys.saved,
    queryFn: ({ signal }) => api.get<JobSummary[]>('/api/jobs/saved', signal),
  });
}

export function useAppliedJobs() {
  return useQuery({
    queryKey: keys.applied,
    queryFn: ({ signal }) => api.get<JobSummary[]>('/api/jobs/applied', signal),
  });
}

type InteractionAction = 'save' | 'unsave' | 'dismiss' | 'undismiss' | 'applied';

export function useJobInteraction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ jobId, action }: { jobId: string; action: InteractionAction }) => {
      switch (action) {
        case 'save':
          return api.post<void>(`/api/jobs/${jobId}/save`);
        case 'unsave':
          return api.delete<void>(`/api/jobs/${jobId}/save`);
        case 'dismiss':
          return api.post<void>(`/api/jobs/${jobId}/dismiss`);
        case 'undismiss':
          return api.delete<void>(`/api/jobs/${jobId}/dismiss`);
        case 'applied':
          return api.post<void>(`/api/jobs/${jobId}/applied`, { applicationStatus: 'APPLIED' });
      }
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: keys.job(variables.jobId) });
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

export function useRematch() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () =>
      api.post<{ jobsScored: number; visibleMatches: number; notice: string | null }>(
        '/api/jobs/rematch',
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['jobs'] });
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

/* --------------------------------------------------------------- Sources */

export function useSources(states: string[], page = 0, size = 50) {
  const params = { state: states, page, size };
  return useQuery({
    queryKey: keys.sources(params),
    queryFn: ({ signal }) =>
      api.get<Page<SourceSummary>>(`/api/sources${queryString(params)}`, signal),
    placeholderData: (previous) => previous,
  });
}

export function useSource(sourceId: string | undefined) {
  return useQuery({
    queryKey: keys.source(sourceId ?? ''),
    queryFn: ({ signal }) => api.get<SourceDetail>(`/api/sources/${sourceId}`, signal),
    enabled: Boolean(sourceId),
  });
}

export function useSourceStats() {
  return useQuery({
    queryKey: keys.sourceStats,
    queryFn: ({ signal }) => api.get<SourceStats>('/api/sources/stats', signal),
  });
}

export function useAdapters() {
  return useQuery({
    queryKey: keys.adapters,
    queryFn: ({ signal }) => api.get<AdapterInfo[]>('/api/sources/adapters', signal),
    staleTime: 60 * 60 * 1000,
  });
}

/* --------------------------------------------------------- Notifications */

export function useNotifications(page = 0) {
  return useQuery({
    queryKey: [...keys.notifications, page],
    queryFn: ({ signal }) =>
      api.get<Page<NotificationView>>(`/api/notifications?page=${page}&size=30`, signal),
  });
}

export function useUnreadCount() {
  return useQuery({
    queryKey: keys.unreadCount,
    queryFn: ({ signal }) => api.get<{ count: number }>('/api/notifications/unread-count', signal),
    // A quiet poll: enough to feel live, not enough to be a load problem.
    refetchInterval: 60_000,
  });
}

export function useMarkNotificationsRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (notificationId?: string) => {
      if (notificationId) {
        await api.post<void>(`/api/notifications/${notificationId}/read`);
        return;
      }
      await api.post<{ updated: number }>('/api/notifications/read-all');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.notifications });
      queryClient.invalidateQueries({ queryKey: keys.unreadCount });
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

/* ----------------------------------------------------------------- Admin */

export function useSystemStats() {
  return useQuery({
    queryKey: keys.systemStats,
    queryFn: ({ signal }) => api.get<SystemStats>('/api/admin/ops/stats', signal),
    refetchInterval: 30_000,
  });
}

/** Adapters an admin defined as data. Platform operators only. */
export function useAdapterDefinitions() {
  return useQuery({
    queryKey: ['admin', 'adapters'],
    queryFn: ({ signal }) => api.get<AdapterDefinition[]>('/api/admin/adapters', signal),
  });
}

/**
 * Creates (no key), updates (key and body) or switches a definition on or off
 * (key and toggle). The adapter list every source dialog reads is refreshed
 * too, since a saved definition is an adapter sources can be registered with.
 */
export function useSaveAdapterDefinition() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      key,
      body,
      toggle,
    }: {
      key: string | null;
      body?: unknown;
      toggle?: 'enable' | 'disable';
    }) => {
      if (key && toggle) {
        return api.post<AdapterDefinition>(`/api/admin/adapters/${encodeURIComponent(key)}/${toggle}`);
      }
      return key
        ? api.put<AdapterDefinition>(`/api/admin/adapters/${encodeURIComponent(key)}`, body)
        : api.post<AdapterDefinition>('/api/admin/adapters', body);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'adapters'] });
      queryClient.invalidateQueries({ queryKey: keys.adapters });
    },
  });
}

export function useAdminSourceAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sourceId, action, body }: { sourceId: string; action: string; body?: unknown }) =>
      api.post<unknown>(`/api/admin/sources/${sourceId}/${action}`, body),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: keys.source(variables.sourceId) });
      queryClient.invalidateQueries({ queryKey: ['sources'] });
      queryClient.invalidateQueries({ queryKey: keys.systemStats });
    },
  });
}

/* ------------------------------------------------ Company source discovery */

/**
 * Resolving typed company names to domains.
 *
 * <p>A mutation rather than a query even though it reads: it reaches out to
 * candidate domains, so it must happen when the operator asks and never on a
 * render, a refetch or a cache revalidation.
 */
export function useResolveCompanies() {
  return useMutation({
    mutationFn: (companyNames: string[]) =>
      api.post<CompanyDiscoveryResult>('/api/admin/sources/discover-by-company', { companyNames }),
  });
}

/**
 * Probing the domains an operator confirmed, and registering what has a board.
 *
 * <p>Invalidates the source registry because this is the call that can add to
 * it. Nothing here decides whether a source may be used — a registered source
 * lands at DISCOVERED and still has to pass the policy gate.
 */
export function useDiscoverConfirmedDomains() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      confirmedDomains,
      companyNames,
    }: {
      confirmedDomains: string[];
      companyNames: string[];
    }) =>
      api.post<CompanyDiscoveryResult>('/api/admin/sources/discover-by-company', {
        companyNames,
        confirmedDomains,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sources'] });
      queryClient.invalidateQueries({ queryKey: ['source-stats'] });
    },
  });
}

/* --------------------------------------------------- Placement workflow */

/* --------------------------------------------- Privacy: resumes, consent, erasure */

/** Every resume the student has uploaded, newest first. */
export function useResumes() {
  return useQuery({
    queryKey: keys.resumes,
    queryFn: ({ signal }) => api.get<ResumeSummary[]>('/api/candidate/resumes', signal),
  });
}

/** Deletes one of the student's own resumes: file, record, text and any reading from it. */
export function useDeleteResume() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (resumeId: string) => api.delete<void>(`/api/candidate/resumes/${resumeId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.resumes });
      queryClient.invalidateQueries({ queryKey: keys.profile });
      queryClient.invalidateQueries({ queryKey: keys.pendingProposal });
      queryClient.invalidateQueries({ queryKey: keys.dashboard });
    },
  });
}

const PURPOSE_PATH: Record<ConsentPurpose, string> = {
  PRIVACY_NOTICE: 'privacy-notice',
  RESUME_PROCESSING: 'resume-processing',
  AI_PROCESSING: 'ai-processing',
};

/** Current notices, what the student has agreed to, and their consent history. */
export function useConsents() {
  return useQuery({
    queryKey: keys.consents,
    queryFn: ({ signal }) => api.get<ConsentOverview>('/api/consents', signal),
  });
}

export function useAcceptConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ purpose, noticeVersionId }: { purpose: ConsentPurpose; noticeVersionId: string }) =>
      api.post<ConsentOverview>(`/api/consents/${PURPOSE_PATH[purpose]}/accept`, {
        noticeVersionId,
        source: 'SETTINGS',
      }),
    onSuccess: (overview) => queryClient.setQueryData(keys.consents, overview),
  });
}

/** Withdrawing AI consent also removes unanswered AI readings, so the pending review is refreshed too. */
export function useWithdrawConsent() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (purpose: ConsentPurpose) =>
      api.post<ConsentOverview>(`/api/consents/${PURPOSE_PATH[purpose]}/withdraw`, { source: 'SETTINGS' }),
    onSuccess: (overview) => {
      queryClient.setQueryData(keys.consents, overview);
      queryClient.invalidateQueries({ queryKey: keys.pendingProposal });
    },
  });
}

/** The student's latest erasure request, or undefined when there has never been one. */
export function useErasure() {
  return useQuery({
    queryKey: keys.erasure,
    queryFn: ({ signal }) => api.get<ErasureView | undefined>('/api/candidate/erasure', signal),
  });
}

export function useRequestErasure() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<ErasureView>('/api/candidate/erasure'),
    onSuccess: (view) => queryClient.setQueryData(keys.erasure, view),
  });
}

export function useCancelErasure() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<ErasureView>('/api/candidate/erasure/cancel'),
    onSuccess: (view) => queryClient.setQueryData(keys.erasure, view),
  });
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** Downloads everything CareerFlux holds about the signed-in student, as JSON. */
export async function downloadMyData(): Promise<void> {
  const data = await api.get<unknown>('/api/candidate/export');
  saveBlob(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }), exportFileName());
}

/**
 * Downloads one of the student's own resume files. Fetched with the session's
 * token rather than opened as a link, because the file route requires it.
 */
export async function downloadResumeFile(resume: ResumeSummary): Promise<void> {
  const response = await fetch(`/api/candidate/resumes/${resume.id}/file`, {
    headers: tokenStore.access ? { Authorization: `Bearer ${tokenStore.access}` } : {},
  });
  if (!response.ok) {
    throw new Error('That file could not be downloaded.');
  }
  saveBlob(await response.blob(), resume.originalFilename || 'resume');
}

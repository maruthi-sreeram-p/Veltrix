/** Shapes returned by the CareerFlux API. Mirrors the backend DTOs. */

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  violations?: { field: string; message: string }[];
  details?: Record<string, unknown>;
}

/**
 * The two roles the platform recognises. Kept as a union rather than a plain
 * string so a rename on the server breaks the build here instead of silently
 * turning a comparison into dead code.
 */
export type UserRole = 'USER' | 'ADMIN';

export interface SessionUser {
  id: string;
  email: string;
  fullName: string;
  role: UserRole;
  /**
   * What this account may do, as granted by its role. Prefer checking a
   * permission over comparing the role: the question a screen actually has is
   * "may they do this?", and asking it that way survives new roles being added.
   */
  permissions: string[];
  candidateId: string | null;
  onboardingStage: OnboardingStage | null;
}

/** Convenience for the checks screens actually make. */
export function can(user: SessionUser | null, permission: string): boolean {
  return user?.permissions?.includes(permission) ?? false;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  user: SessionUser;
}

export type OnboardingStage =
  | 'RESUME_UPLOAD'
  | 'PROFILE_REVIEW'
  | 'PREFERENCES'
  | 'COMPLETE';

/* ------------------------------------------------------------- Candidate */

export interface SkillItem {
  id?: string;
  name: string;
  slug?: string;
  category?: string;
  proficiency?: string | null;
  years?: number | null;
  origin?: string;
  evidence?: string | null;
}

export interface ExperienceItem {
  id?: string;
  companyName: string;
  title: string;
  location?: string | null;
  startDate?: string | null;
  endDate?: string | null;
  current: boolean;
  description?: string | null;
}

export interface EducationItem {
  id?: string;
  institution: string;
  degree?: string | null;
  fieldOfStudy?: string | null;
  startYear?: number | null;
  endYear?: number | null;
  grade?: string | null;
}

export interface PreferencesPayload {
  targetRoles: string[];
  industries: string[];
  locations: string[];
  workModes: string[];
  employmentTypes: string[];
  preferredCompanies: string[];
  salaryMin?: number | null;
  salaryMax?: number | null;
  salaryCurrency?: string | null;
  salaryPeriod?: string | null;
  openToRelocation: boolean;
  minExperienceYears?: number | null;
  maxExperienceYears?: number | null;
  immediateAlerts: boolean;
  dailyDigest: boolean;
}

export interface ResumeSummary {
  id: string;
  originalFilename: string;
  contentType: string | null;
  sizeBytes: number | null;
  parseStatus: 'PENDING' | 'PARSING' | 'PARSED' | 'NEEDS_REVIEW' | 'FAILED';
  parseEngine: string | null;
  parseError: string | null;
  uploadedAt: string;
  parsedAt: string | null;
}

export interface CandidateProfile {
  id: string;
  fullName: string;
  email: string;
  headline: string | null;
  summary: string | null;
  location: string | null;
  phone: string | null;
  personalEmail: string | null;
  personalEmailVerified: boolean;
  linkedinUrl: string | null;
  githubUrl: string | null;
  portfolioUrl: string | null;
  primaryRole: string | null;
  seniority: string | null;
  yearsExperience: number | null;
  onboardingStage: OnboardingStage;
  profileCompleteness: number;
  skills: SkillItem[];
  experiences: ExperienceItem[];
  education: EducationItem[];
  preferences: PreferencesPayload | null;
  resume: ResumeSummary | null;
}

export interface ResumeParseResult {
  resume: ResumeSummary;
  engine: string;
  aiAssisted: boolean;
  notice: string | null;
  /** Unchanged by the upload. Nothing is written until the proposal is approved. */
  profile: CandidateProfile;
  /** The reading waiting for review, or null when nothing was worth asking about. */
  proposalId: string | null;
}

/* --------------------------------------------------- AI profile proposals */

/**
 * The verdict on one thing the resume reader found.
 *
 * MISSING and UNCHANGED are shown but never actionable, and INFORMATION_FOUND
 * is the academic record: read from the resume, displayed, and not something
 * this workflow may write.
 */
export type ProposalItemState =
  | 'NEW'
  | 'UNCHANGED'
  | 'CONFLICT'
  | 'MISSING'
  | 'UNCERTAIN'
  | 'INFORMATION_FOUND';

export type ProposalSection =
  | 'PERSONAL'
  | 'PROFESSIONAL'
  | 'LINKS'
  | 'SKILLS'
  | 'EXPERIENCE'
  | 'EDUCATION'
  | 'ACADEMIC';

export interface ProposedItem {
  key: string;
  section: ProposalSection;
  label: string;
  state: ProposalItemState;
  currentValue: string | null;
  proposedValue: string | null;
  /** Whether accepting this could change anything. The server enforces it too. */
  decidable: boolean;
  editable: boolean;
  data: Record<string, string>;
  note: string | null;
}

export interface ProposalView {
  id: string;
  resumeId: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED';
  engine: string;
  aiAssisted: boolean;
  createdAt: string;
  reviewedAt: string | null;
  items: ProposedItem[];
}

export type DecisionAction = 'ACCEPT' | 'REJECT' | 'EDIT';

export interface Decision {
  key: string;
  action: DecisionAction;
  value: string | null;
}

export interface ReviewResult {
  proposal: ProposalView;
  applied: string[];
  profile: CandidateProfile;
}

/* ------------------------------------------------------------------ Jobs */

export interface CompanyRef {
  id: string;
  name: string;
  logoUrl: string | null;
  website: string | null;
  industry: string | null;
}

export interface SkillRef {
  name: string;
  slug: string;
  category: string;
  requirement: 'REQUIRED' | 'PREFERRED';
  extractedBy: 'DICTIONARY' | 'AI' | 'SOURCE';
}

export interface MatchComponentView {
  kind: 'STRENGTH' | 'GAP' | 'NEUTRAL';
  dimension: 'SKILLS' | 'EXPERIENCE' | 'ROLE' | 'LOCATION' | 'SENIORITY' | 'CONTEXT';
  label: string;
  detail: string | null;
}

export type MatchTier = 'EXCELLENT' | 'STRONG' | 'MODERATE' | 'WEAK' | 'HIDDEN';

export interface MatchAnalysis {
  overall: number;
  tier: MatchTier;
  skills: number;
  experience: number;
  role: number;
  location: number;
  seniority: number;
  strengths: MatchComponentView[];
  gaps: MatchComponentView[];
  context: MatchComponentView[];
  narrative: string | null;
  narrativeEngine: string | null;
  scorerVersion: string;
  computedAt: string;
}

export interface ProvenanceEntry {
  sourceId: string;
  sourceName: string;
  sourceType: string;
  atsProvider: string;
  discoveryMethod: string;
  sourceUrl: string | null;
  sourceState: SourceState;
  healthStatus: SourceHealthStatus;
  accessPolicy: string | null;
  policyVerifiedAt: string | null;
  firstObservedAt: string;
  lastObservedAt: string;
  observationCount: number;
  active: boolean;
  sampleData: boolean;
}

export interface ChangeEntry {
  changeType: string;
  summary: string;
  fieldName: string | null;
  previousValue: string | null;
  newValue: string | null;
  detectedAt: string;
}

export interface JobSummary {
  id: string;
  title: string;
  company: CompanyRef | null;
  location: string | null;
  city: string | null;
  workMode: string;
  employmentType: string;
  seniority: string;
  experience: { min: number | null; max: number | null } | null;
  salary: {
    min: number | null;
    max: number | null;
    currency: string | null;
    period: string | null;
  } | null;
  status: string;
  postedAt: string | null;
  firstObservedAt: string;
  lastObservedAt: string;
  sourceCount: number;
  primarySourceName: string | null;
  primarySourceType: string | null;
  sampleData: boolean;
  topSkills: SkillRef[];
  match: MatchAnalysis | null;
  interaction: {
    saved: boolean;
    dismissed: boolean;
    applied: boolean;
    applicationStatus: string | null;
  };
  recentChangeCount: number;
}

export interface JobDetail {
  summary: JobSummary;
  description: string | null;
  responsibilities: string | null;
  requirements: string | null;
  applyUrl: string | null;
  skills: SkillRef[];
  provenance: ProvenanceEntry[];
  changes: ChangeEntry[];
}

export interface JobPage {
  content: JobSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/* --------------------------------------------------------------- Sources */

export type SourceState =
  | 'DISCOVERED'
  | 'CLASSIFIED'
  | 'POLICY_REVIEW'
  | 'APPROVED'
  | 'ACTIVE'
  | 'PENDING_REVIEW'
  | 'DEGRADED'
  | 'BLOCKED'
  | 'RETIRED';

export type SourceHealthStatus =
  | 'UNKNOWN'
  | 'HEALTHY'
  | 'DEGRADED'
  | 'FAILING'
  | 'UNREACHABLE';

export interface PolicyView {
  robotsStatus: string;
  robotsUrl: string | null;
  robotsRule: string | null;
  robotsCheckedAt: string | null;
  crawlDelaySeconds: number | null;
  tosStatus: string;
  tosUrl: string | null;
  tosNotes: string | null;
  tosReviewedAt: string | null;
  tosReviewedBy: string | null;
  accessPolicy: string;
  requiresAuthentication: boolean;
  requiresCaptcha: boolean;
  hasAntiBot: boolean;
  paywalled: boolean;
  allowedFields: string | null;
  decision: string;
  decisionReason: string | null;
  decidedBy: string | null;
  verifiedAt: string | null;
}

export interface SourceSummary {
  id: string;
  name: string;
  baseUrl: string;
  sourceType: string;
  atsProvider: string;
  adapterKey: string | null;
  discoveryMethod: string;
  state: SourceState;
  healthStatus: SourceHealthStatus;
  companyName: string | null;
  companyId: string | null;
  discoveredAt: string;
  stateChangedAt: string;
  lastSuccessfulSyncAt: string | null;
  lastHealthCheckAt: string | null;
  nextReviewAt: string | null;
  consecutiveFailures: number;
  /** -1 means no sync has been attempted, which is not the same as 0%. */
  reliabilityPercent: number;
  jobsIngestedTotal: number;
  activeJobCount: number;
  rateLimitPerMinute: number;
  sampleData: boolean;
  needsAttention: boolean;
  policy: PolicyView | null;
}

export interface HealthCheckView {
  status: SourceHealthStatus;
  httpStatus: number | null;
  latencyMs: number | null;
  jobsSeen: number | null;
  message: string | null;
  checkedAt: string;
}

export interface LifecycleEntry {
  fromState: SourceState | null;
  toState: SourceState;
  reason: string | null;
  actor: string;
  occurredAt: string;
}

export interface IngestionRunView {
  id: string;
  status: 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED' | 'SKIPPED';
  trigger: string;
  correlationId: string;
  rawCount: number;
  newCount: number;
  updatedCount: number;
  duplicateCount: number;
  closedCount: number;
  errorCount: number;
  errorMessage: string | null;
  startedAt: string;
  finishedAt: string | null;
}

export interface SourceDetail {
  summary: SourceSummary;
  verdict: { passed: boolean; blockers: string[]; warnings: string[] };
  recentHealth: HealthCheckView[];
  lifecycle: LifecycleEntry[];
  recentRuns: IngestionRunView[];
  allowedNextStates: SourceState[];
  notes: string | null;
}

export interface SourceStats {
  total: number;
  byState: Record<string, number>;
  byHealth: Record<string, number>;
  needingAttention: number;
  jobsFromActiveSources: number;
  ingestionTransport: string;
  aiEnabled: boolean;
}

/** What an admin types to describe a JSON feed; blank means not mapped. */
export interface AdapterDefinitionInput {
  adapterKey: string;
  displayName: string;
  description: string;
  documentationUrl: string;
  endpointTemplate: string;
  itemsPath: string;
  idPath: string;
  titlePath: string;
  locationPath: string;
  descriptionPath: string;
  applyUrlPath: string;
  applyUrlTemplate: string;
  postedAtPath: string;
  employmentTypePath: string;
  departmentPath: string;
}

export interface AdapterDefinition {
  adapterKey: string;
  displayName: string;
  description: string | null;
  documentationUrl: string | null;
  endpointTemplate: string;
  itemsPath: string | null;
  idPath: string;
  titlePath: string;
  locationPath: string | null;
  descriptionPath: string | null;
  applyUrlPath: string | null;
  applyUrlTemplate: string | null;
  postedAtPath: string | null;
  employmentTypePath: string | null;
  departmentPath: string | null;
  enabled: boolean;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface AdapterPreview {
  fetched: boolean;
  endpoint: string;
  robotsStatus: string | null;
  message: string;
  total: number;
  unreadable: number;
  postings: {
    externalId: string;
    title: string;
    location: string | null;
    employmentType: string | null;
    department: string | null;
    applyUrl: string | null;
    postedAt: string | null;
    hasDescription: boolean;
  }[];
}

export interface AdapterInfo {
  key: string;
  displayName: string;
  sourceType: string;
  atsProvider: string;
  intendedAccessPolicy: string;
  documentationUrl: string | null;
  description: string;
}

/* ------------------------------------------------------------- Dashboard */

export interface DashboardSummary {
  greetingName: string;
  newSinceLastVisit: number;
  excellentMatches: number;
  strongMatches: number;
  totalVisibleMatches: number;
  sourcesMonitored: number;
  sourcesActive: number;
  openJobs: number;
  lastMatchComputedAt: string | null;
  profileCompleteness: number;
  onboardingStage: OnboardingStage;
  hasResume: boolean;
  aiEnabled: boolean;
  notice: string | null;
}

export interface ActivityItem {
  kind: string;
  title: string;
  detail: string;
  occurredAt: string;
  jobId: string | null;
  sourceId: string | null;
}

export interface DashboardResponse {
  summary: DashboardSummary;
  recommendations: JobSummary[];
  recentActivity: ActivityItem[];
  unreadNotifications: number;
  engagement: { saved: number; applied: number; dismissed: number };
}

/* --------------------------------------------------------- Notifications */

export interface NotificationView {
  id: string;
  category: string;
  priority: 'IMMEDIATE' | 'HIGH' | 'DIGEST' | 'LOW';
  title: string;
  body: string | null;
  jobId: string | null;
  jobTitle: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

/* ----------------------------------------------------------------- Admin */

export interface SystemStats {
  totalJobs: number;
  openJobs: number;
  closedJobs: number;
  jobsIngestedLast24h: number;
  jobChangesLast24h: number;
  ingestionRunsLast24h: number;
  failedRunsLast24h: number;
  pendingPipelineEvents: number;
  failedPipelineEvents: number;
  pipelineEventsLast24hByTopic: Record<string, number>;
  users: number;
  admins: number;
  ingestionTransport: string;
  aiEnabled: boolean;
  aiModel: string;
  sources: SourceStats;
}

export interface PipelineEventView {
  id: string;
  topic: string;
  eventKey: string;
  status: 'PENDING' | 'PROCESSED' | 'FAILED';
  attempts: number;
  error: string | null;
  createdAt: string;
  processedAt: string | null;
}

/**
 * One audit row. People are named by account id and role, never by email; the
 * label is the role, or a system actor's name.
 */
export interface AuditView {
  id: string;
  actorUserId: string | null;
  actorRole: string | null;
  actorLabel: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  detail: string | null;
  occurredAt: string;
}

/* ------------------------------------------------- Company source discovery */

/**
 * What the resolver concluded about one typed company name.
 *
 * <p>Three outcomes, three different next steps for the operator: confirm,
 * choose, or type a domain in. Decided by the server; see CompanyResolver.java.
 */
export type ResolutionOutcome = 'RESOLVED' | 'AMBIGUOUS' | 'NOT_FOUND';

/** Why a domain is being proposed. Shown so an operator can weigh it. */
export type CandidateEvidence = 'REGISTRY' | 'SEED_LIST' | 'VERIFIED_SITE';

export interface DomainCandidate {
  domain: string;
  evidence: CandidateEvidence;
  detail: string;
}

export interface ResolvedCompany {
  companyName: string;
  slug: string;
  outcome: ResolutionOutcome;
  detail: string;
  candidates: DomainCandidate[];
}

/** One source that discovery registered, straight from the existing service. */
export interface DiscoveredSource {
  sourceId: string;
  name: string;
  domain: string;
  provider: string;
  boardToken: string;
  howFound: string;
  detail: string;
}

/**
 * The reply to both phases of discover-by-company.
 *
 * <p>A resolve fills the first three lists and leaves the rest empty; a
 * confirmed-domain call does the opposite. Keeping one shape means the screen
 * does not have to model two endpoints.
 */
export interface CompanyDiscoveryResult {
  resolved: ResolvedCompany[];
  ambiguous: ResolvedCompany[];
  notFound: ResolvedCompany[];
  registered: DiscoveredSource[];
  alreadyKnown: string[];
  withoutBoard: string[];
}

/* ------------------------------------------------ Consent, export, erasure */

export type ConsentPurpose = 'PRIVACY_NOTICE' | 'RESUME_PROCESSING' | 'AI_PROCESSING';

/** One published version of a notice, exactly as it is shown. */
export interface NoticeView {
  id: string;
  purpose: ConsentPurpose;
  version: string;
  effectiveFrom: string;
  checksum: string;
  /** Engineering placeholder text, not approved wording. */
  placeholder: boolean;
  body: string;
}

export interface ConsentEvent {
  id: string;
  purpose: ConsentPurpose;
  action: 'ACCEPTED' | 'WITHDRAWN';
  noticeVersionId: string;
  noticeVersion: string;
  source: string;
  at: string;
}

export interface PurposeState {
  purpose: ConsentPurpose;
  currentNotice: NoticeView;
  active: boolean;
  /** Agreed to an older notice; the current text has not been agreed to. */
  reconsentRequired: boolean;
  latest: ConsentEvent | null;
}

export interface ConsentOverview {
  purposes: PurposeState[];
  history: ConsentEvent[];
}

export type ErasureStatus = 'GRACE_PERIOD' | 'PROCESSING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

export interface ErasureView {
  id: string;
  status: ErasureStatus;
  requestedByRole: string;
  requestedAt: string;
  graceEndsAt: string;
  cancellable: boolean;
  cancelledAt: string | null;
  completedAt: string | null;
}

import type { ChatMessage, AgentStepView } from '@/api/chat'
import type { AgentRunSnapshot } from '@/api/agentRun'

export function isActiveAgentRun(status?: string | null): boolean {
  return status === 'RUNNING' || status === 'WAITING_ACTION' || status === 'WAITING_APPROVAL' || status === 'RECOVERY_QUEUED'
}

/** Merge only the selected run; a slow response must never replace another conversation. */
export function mergeAgentRunSnapshot(message: ChatMessage, run: AgentRunSnapshot): ChatMessage {
  if (message.agentRunKey !== run.runKey) return message
  const terminal = !isActiveAgentRun(run.status)
  const steps: AgentStepView[] = run.steps.filter(step => step.type === 'tool' || step.type === 'llm').map(step => ({
    seq: step.seq, type: step.type as AgentStepView['type'], toolCode: step.toolCode,
    actionId: step.actionId, status: step.status, argsPreview: step.inputPreview,
    resultPreview: step.outputPreview, latencyMs: step.latencyMs,
  }))
  return {
    ...message, historyId: run.historyId ?? message.historyId, agentStatus: run.status, agentSteps: steps,
    agentCanRecover: run.canRecover, agentRecoveryReason: run.recoveryReason,
    content: terminal ? ((run.status === 'FAILED' || run.status === 'INTERRUPTED') && run.errorMessage ? run.errorMessage : run.answer ?? message.content) : message.content,
    pendingApprovals: run.actions.filter(action => action.status === 'PENDING_APPROVAL').map(action => ({
      runId: run.runKey, actionId: action.id, toolCode: action.toolName, toolName: action.toolName,
      argsPreview: action.argsPreview, canApprove: action.canApprove === true,
    })),
  }
}

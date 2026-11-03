import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = await readFile(new URL('../src/utils/agentRunState.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } }).outputText
const { mergeAgentRunSnapshot, isActiveAgentRun } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const message = { id: 1, role: 'assistant', content: '已有流式内容', createTime: '', agentRunKey: 'run-1' }
const snapshot = { runKey: 'run-1', status: 'WAITING_ACTION', steps: [], actions: [] }

test('background execution preserves streamed content until the committed final answer arrives', () => {
  const waiting = mergeAgentRunSnapshot(message, { ...snapshot, answer: '暂停占位内容' })
  assert.equal(waiting.content, message.content)
  assert.equal(isActiveAgentRun(waiting.agentStatus), true)
  const done = mergeAgentRunSnapshot(waiting, { ...snapshot, status: 'COMPLETED', answer: '工单已创建' })
  assert.equal(done.content, '工单已创建')
  assert.equal(isActiveAgentRun(done.agentStatus), false)
})

test('a stale response from another run cannot replace the current message', () => {
  assert.equal(mergeAgentRunSnapshot(message, { ...snapshot, runKey: 'another-run' }), message)
})

test('only undecided approvals remain actionable after a refresh', () => {
  const result = mergeAgentRunSnapshot(message, { ...snapshot, actions: [
    { id: 1, toolName: '创建工单', status: 'PENDING_APPROVAL', canApprove: true },
    { id: 2, toolName: '另一个动作', status: 'READY', canApprove: true },
  ] })
  assert.equal(result.pendingApprovals.length, 1)
  assert.equal(result.pendingApprovals[0].actionId, 1)
  assert.equal(result.pendingApprovals[0].canApprove, true)
})

test('background failure shows its cause instead of leaving a waiting placeholder', () => {
  const failed = mergeAgentRunSnapshot(message, { ...snapshot, status: 'FAILED', errorMessage: '模型调用超时' })
  assert.equal(failed.content, '模型调用超时')
})

test('an interrupted worker stops polling and replaces the waiting placeholder with its cause', () => {
  const interrupted = mergeAgentRunSnapshot(message, {
    ...snapshot, status: 'INTERRUPTED', answer: '暂停占位内容', errorMessage: '任务执行已中断，请核对业务动作',
  })
  assert.equal(interrupted.agentStatus, 'INTERRUPTED')
  assert.equal(isActiveAgentRun(interrupted.agentStatus), false)
  assert.equal(interrupted.content, '任务执行已中断，请核对业务动作')
})

test('a queued recovery keeps polling and its final answer replaces the interruption', () => {
  const queued = mergeAgentRunSnapshot(message, { ...snapshot, status: 'RECOVERY_QUEUED', canRecover: false })
  assert.equal(isActiveAgentRun(queued.agentStatus), true)
  assert.equal(queued.agentCanRecover, false)
  const completed = mergeAgentRunSnapshot(queued, { ...snapshot, status: 'COMPLETED', answer: '恢复后的回答' })
  assert.equal(completed.content, '恢复后的回答')
  assert.equal(isActiveAgentRun(completed.agentStatus), false)
})

test('the server recovery decision and explanation replace stale local recovery controls', () => {
  const result = mergeAgentRunSnapshot({ ...message, agentCanRecover: true }, {
    ...snapshot, status: 'FAILED', canRecover: false, recoveryReason: '已有业务动作，需要核对',
  })
  assert.equal(result.agentCanRecover, false)
  assert.equal(result.agentRecoveryReason, '已有业务动作，需要核对')
})

test('a cancelled run stops polling and does not become a failed recoverable run', () => {
  const cancelled = mergeAgentRunSnapshot(message, {
    ...snapshot, status: 'CANCELLED', answer: '任务已取消', canRecover: false,
  })
  assert.equal(cancelled.agentStatus, 'CANCELLED')
  assert.equal(cancelled.agentCanRecover, false)
  assert.equal(isActiveAgentRun(cancelled.agentStatus), false)
  assert.equal(cancelled.content, '任务已取消')
})

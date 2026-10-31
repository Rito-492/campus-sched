// 共享契约限制待办关联查询最多6并发；失败保留为独立结果。
export async function mapSettled<T, R>(
  items: T[],
  fn: (item: T) => Promise<R>,
  active = () => true,
): Promise<PromiseSettledResult<R>[]> {
  const result: PromiseSettledResult<R>[] = []
  let next = 0
  await Promise.all(
    Array.from({ length: Math.min(6, items.length) }, async () => {
      while (active()) {
        const index = next++
        if (index >= items.length) return
        try {
          result[index] = { status: 'fulfilled', value: await fn(items[index]) }
        } catch (reason) {
          result[index] = { status: 'rejected', reason }
        }
      }
    }),
  )
  return result
}

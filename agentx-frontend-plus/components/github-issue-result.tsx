interface GitHubIssueResultProps {
  externalResourceId: string
  repository: string | null | undefined
}

/** Only link a canonical GitHub issue URL within the run's approved repository. */
export function getValidatedGitHubIssueUrl(resourceId: string, repository: string | null | undefined): string | null {
  if (!repository || !/^[A-Za-z0-9_.-]{1,39}\/[A-Za-z0-9_.-]{1,100}$/.test(repository)) return null
  try {
    const url = new URL(resourceId)
    if (url.protocol !== "https:" || url.hostname.toLowerCase() !== "github.com" || url.port
      || url.username || url.password || url.search || url.hash) return null
    const match = /^\/([^/]+)\/([^/]+)\/issues\/([1-9]\d*)$/.exec(url.pathname)
    if (!match) return null
    const target = `${match[1]}/${match[2]}`
    if (target.toLowerCase() !== repository.toLowerCase()) return null
    return url.toString()
  } catch {
    return null
  }
}

export function GitHubIssueResult({ externalResourceId, repository }: GitHubIssueResultProps) {
  const issueUrl = getValidatedGitHubIssueUrl(externalResourceId, repository)
  return issueUrl ? (
    <a href={issueUrl} target="_blank" rel="noreferrer" className="break-all text-primary underline">
      {externalResourceId}
    </a>
  ) : (
    <span className="break-all font-mono text-xs">{externalResourceId}</span>
  )
}

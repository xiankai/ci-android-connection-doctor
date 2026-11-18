#!/usr/bin/env node
import { setTimeout as sleep } from "node:timers/promises";

const env = (name, fallback) => process.env[name] ?? fallback;

const repo = env("RELAY_GITHUB_REPO", "xiankai/ci-android-connection-doctor");
const workflow = env("RELAY_WORKFLOW", "ci.yml");
const sha = env("RELAY_SHA", "");
const firstPollSeconds = Number(env("RELAY_FIRST_POLL_SECONDS", "480"));
const pollSeconds = Number(env("RELAY_POLL_SECONDS", "120"));
const deadline = Date.now() + Number(env("RELAY_TIMEOUT_MINUTES", "50")) * 60_000;
// stack-reviewer-ignore: the query already filters by workflow, event and head_sha, so a run GitHub returns is never dropped by newest(); a wrong RELAY_WORKFLOW 404s and fails at once
let noRunDeadline = Date.now() + Number(env("RELAY_NO_RUN_MINUTES", "12")) * 60_000;
const token = env("RELAY_GITHUB_TOKEN", "");

if (!/^[0-9a-f]{40}$/.test(sha)) {
  console.error(`::error::RELAY_SHA must be a full commit SHA, got '${sha}'`);
  process.exit(2);
}

const runsUrl =
  `https://api.github.com/repos/${repo}/actions/workflows/${workflow}/runs` +
  `?head_sha=${sha}&event=push&per_page=20`;
const workflowPage = `https://github.com/${repo}/actions/workflows/${workflow}`;

async function fetchRuns() {
  const headers = {
    Accept: "application/vnd.github+json",
    "User-Agent": "forgejo-github-ci-relay",
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const response = await fetch(runsUrl, { headers });
  if (response.ok) return { runs: (await response.json()).workflow_runs ?? [] };
  const remaining = response.headers.get("x-ratelimit-remaining");
  if ((response.status === 403 || response.status === 429) && remaining === "0") {
    const resetAt = Number(response.headers.get("x-ratelimit-reset")) * 1000;
    return { rateLimitedUntil: resetAt || Date.now() + 60_000 };
  }
  return { failure: `GitHub answered HTTP ${response.status} for ${runsUrl}` };
}

function newest(runs) {
  return runs
    .filter((run) => run.head_sha === sha)
    .sort((a, b) => b.run_number - a.run_number || b.run_attempt - a.run_attempt)[0];
}

async function waitUntil(until) {
  const wait = Math.max(0, Math.min(until, deadline) - Date.now());
  if (wait > 0) await sleep(wait);
}

console.log(`Waiting ${firstPollSeconds}s before asking GitHub about ${sha}: ${workflowPage}`);
await waitUntil(Date.now() + firstPollSeconds * 1000);

let announced = "";
while (Date.now() < deadline) {
  const result = await fetchRuns();

  if (result.failure) {
    console.error(`::error::${result.failure}`);
    console.error(`A 404 means ${repo} is private or ${workflow} is not on GitHub yet.`);
    process.exit(1);
  }

  if (result.rateLimitedUntil) {
    const resumeAt = result.rateLimitedUntil + 5_000;
    const waitMs = resumeAt - Date.now();
    if (resumeAt > deadline) {
      console.error(`::error::GitHub rate limit runs until ${new Date(result.rateLimitedUntil).toISOString()}, past this job's deadline.`);
      process.exit(1);
    }
    if (waitMs <= 0) {
      console.error(`::error::GitHub reports a rate limit that reset at ${new Date(result.rateLimitedUntil).toISOString()} and refuses anyway.`);
      process.exit(1);
    }
    console.log(`GitHub rate limit reached; waiting until ${new Date(resumeAt).toISOString()}`);
    const pausedAt = Date.now();
    await sleep(waitMs);
    noRunDeadline += Date.now() - pausedAt;
    continue;
  }

  const run = newest(result.runs);
  if (!run && Date.now() > noRunDeadline) { // stack-reviewer-ignore: the push mirror syncs on commit within seconds, so no run after 12 minutes means the mirror or GitHub is broken, which should fail loudly rather than wait out the full timeout
    console.error(`::error::No ${workflow} run on GitHub for ${sha} yet.`);
    console.error(`Check that the push mirror carries this branch: ${workflowPage}`);
    process.exit(1);
  }

  if (run && run.html_url !== announced) {
    console.log(`GitHub run: ${run.html_url}`);
    announced = run.html_url;
  }

  if (run?.status === "completed") {
    if (run.conclusion === "success") {
      console.log(`GitHub checks passed: ${run.html_url}`);
      process.exit(0);
    }
    console.error(`::error::GitHub checks ended '${run.conclusion}': ${run.html_url}`);
    process.exit(1);
  }

  console.log(run ? `still ${run.status}` : "waiting for the mirror push to start a run");
  await waitUntil(Date.now() + pollSeconds * 1000);
}

console.error(`::error::Timed out waiting for the GitHub run for ${sha}: ${announced || workflowPage}`);
process.exit(1);

#!/usr/bin/env bun

import {spawnSync} from "node:child_process"
import {existsSync, statSync} from "node:fs"
import {resolve} from "node:path"
import process from "node:process"

const USAGE_EXIT_CODE = 2;
const COMMAND_EXIT_CODE = 3;

const ANSI = {
  reset: "\u001b[0m",
  blue: "\u001b[34m",
  cyan: "\u001b[36m",
  dim: "\u001b[2m",
  green: "\u001b[32m",
  red: "\u001b[31m",
  yellow: "\u001b[33m",
};

const STRUCTURAL_EXCLUDED_CONFIG_KEYS = new Set([
  "core.bare",
  "core.repositoryformatversion",
  "core.worktree",
]);

const SPARSE_EXCLUDED_CONFIG_KEYS = new Set([
  "core.sparsecheckout",
  "core.sparsecheckoutcone",
  "index.sparse",
]);

const STRUCTURAL_EXCLUDED_CONFIG_PREFIXES = [
  "extensions.",
];

class UsageError extends Error {
  constructor(message) {
    super(message);
    this.name = "UsageError";
  }
}

class GitError extends Error {
  constructor(args, cwd, status, stderr, cause) {
    const command = formatCommand("git", args);
    const place = cwd ? ` (cwd: ${cwd})` : "";
    const details = stderr.trim();
    const reason = details.length > 0 ? `\n${details}` : "";
    super(`Command failed: ${command}${place}${reason}`);
    this.name = "GitError";
    this.args = args;
    this.cwd = cwd;
    this.status = status;
    this.stderr = stderr;
    this.cause = cause;
  }
}

function printUsage() {
  console.log([
    "Usage:",
    "  bun community/tools/clone-from-local-cache.mjs [--dry-run] [--quiet] [--no-color] <source-repo-path> <target-repo-path>",
    "",
    "Description:",
    "  Clone from a local source repository using git --local for speed.",
    "  Then normalize target repository to remote truth and mirror local git config.",
    "",
    "Flags:",
    "  --quiet    Disable verbose command logging (verbose is enabled by default)",
    "  --verbose  Explicitly enable verbose command logging",
    "  --dry-run  Print planned actions without creating target clone",
    "  --no-color Disable ANSI colors",
    "  --color    Force-enable ANSI colors",
    "  --help     Print this help",
  ].join("\n"));
}

function detectColorSupport() {
  const env = process["env"] ?? {};
  if (Object.prototype.hasOwnProperty.call(env, "NO_COLOR")) {
    return false;
  }
  const forceColor = env["FORCE_COLOR"];
  if (forceColor !== undefined) {
    return forceColor !== "0";
  }
  const stdout = process["stdout"];
  return Boolean(stdout && stdout["isTTY"] === true);
}

function colorize(text, color, useColor) {
  if (!useColor) {
    return text;
  }
  return `${color}${text}${ANSI.reset}`;
}

function printInfo(message, useColor) {
  console.log(colorize(message, ANSI.blue, useColor));
}

function printWarning(message, useColor) {
  console.log(colorize(message, ANSI.yellow, useColor));
}

function printSuccess(message, useColor) {
  console.log(colorize(message, ANSI.green, useColor));
}

function printError(message, useColor) {
  console.error(colorize(message, ANSI.red, useColor));
}

function printKeyValue(label, value, useColor) {
  console.log(`${colorize(`${label}:`, ANSI.blue, useColor)} ${value}`);
}

function parseArguments(argv) {
  let dryRun = false;
  let verbose = true;
  let useColor = detectColorSupport();
  const positional = [];

  for (const arg of argv) {
    if (arg === "--quiet" || arg === "--no-verbose") {
      verbose = false;
      continue;
    }
    if (arg === "--dry-run") {
      dryRun = true;
      continue;
    }
    if (arg === "--verbose") {
      verbose = true;
      continue;
    }
    if (arg === "--no-color") {
      useColor = false;
      continue;
    }
    if (arg === "--color") {
      useColor = true;
      continue;
    }
    if (arg === "--help") {
      printUsage();
      process.exit(0);
    }
    if (arg.startsWith("--")) {
      throw new UsageError(`Unknown option: ${arg}`);
    }
    positional.push(arg);
  }

  if (positional.length !== 2) {
    throw new UsageError("Expected exactly 2 positional arguments: <source-repo-path> <target-repo-path>");
  }

  return {
    sourceRepoPath: resolve(positional[0]),
    targetRepoPath: resolve(positional[1]),
    dryRun,
    useColor,
    verbose,
  };
}

function formatCommand(command, args) {
  return [command, ...args.map(quoteArg)].join(" ");
}

function quoteArg(value) {
  return /^[-_./:@a-zA-Z0-9]+$/.test(value) ? value : JSON.stringify(value);
}

function runGit(args, options = {}) {
  const {cwd, allowFailure = false, verbose = false, useColor = false, streamOutput = false} = options;
  if (verbose) {
    const commandPrefix = colorize("$", ANSI.dim, useColor);
    const commandText = colorize(formatCommand("git", args), ANSI.cyan, useColor);
    console.log(`${commandPrefix} ${commandText}`);
  }

  const stdio = streamOutput ? "inherit" : ["ignore", "pipe", "pipe"];
  const result = spawnSync("git", args, {
    cwd,
    encoding: "utf8",
    stdio,
  });

  if (result.error) {
    throw new GitError(args, cwd, -1, "", result.error);
  }

  const status = result.status ?? -1;
  const stdout = result.stdout ?? "";
  const stderr = result.stderr ?? "";
  if (status !== 0 && !allowFailure) {
    throw new GitError(args, cwd, status, stderr);
  }

  return {
    status,
    stdout,
    stderr,
  };
}

function ensureDirectoryExists(path, description) {
  if (!existsSync(path)) {
    throw new UsageError(`${description} does not exist: ${path}`);
  }

  let stats;
  try {
    stats = statSync(path);
  }
  catch (error) {
    throw new UsageError(`Cannot access ${description}: ${path}. ${String(error)}`);
  }

  if (!stats.isDirectory()) {
    throw new UsageError(`${description} is not a directory: ${path}`);
  }
}

function ensureSourceIsGitRepository(sourceRepoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", sourceRepoPath, "rev-parse", "--is-inside-work-tree"], {
    allowFailure: true,
    useColor,
    verbose,
  });
  if (result.status !== 0 || result.stdout.trim() !== "true") {
    throw new UsageError(`Source path is not a git work tree: ${sourceRepoPath}`);
  }
}

function ensureTargetDoesNotExist(targetRepoPath) {
  if (existsSync(targetRepoPath)) {
    throw new UsageError(`Target path already exists: ${targetRepoPath}`);
  }
}

function detectOriginUrl(sourceRepoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", sourceRepoPath, "remote", "get-url", "origin"], {
    allowFailure: true,
    useColor,
    verbose,
  });
  if (result.status !== 0) {
    throw new UsageError(`Source repo does not have origin remote configured: ${sourceRepoPath}`);
  }
  const url = result.stdout.trim();
  if (url.length === 0) {
    throw new UsageError(`Origin URL is empty in source repo: ${sourceRepoPath}`);
  }
  return url;
}

function detectOriginDefaultBranch(sourceRepoPath, options) {
  const {verbose, useColor} = options;
  const headRefResult = runGit(
    ["-C", sourceRepoPath, "symbolic-ref", "--quiet", "--short", "refs/remotes/origin/HEAD"],
    {allowFailure: true, useColor, verbose},
  );
  if (headRefResult.status === 0) {
    const value = headRefResult.stdout.trim();
    if (value.startsWith("origin/")) {
      return value.slice("origin/".length);
    }
    if (value.length > 0) {
      return value;
    }
  }

  const remoteShowResult = runGit(["-C", sourceRepoPath, "remote", "show", "origin"], {
    allowFailure: true,
    useColor,
    verbose,
  });
  if (remoteShowResult.status === 0) {
    const match = remoteShowResult.stdout.match(/^\s*HEAD branch:\s*(.+)\s*$/m);
    if (match) {
      const branch = match[1].trim();
      if (branch.length > 0 && branch !== "(unknown)") {
        return branch;
      }
    }
  }

  throw new UsageError(
    [
      `Cannot determine default branch for origin in ${sourceRepoPath}.`,
      "Run: git -C <source-repo-path> remote set-head origin --auto",
    ].join(" "),
  );
}

function buildCloneArgs(sourceRepoPath, targetRepoPath) {
  return [
    "clone",
    "--local",
    "--progress",
    sourceRepoPath,
    targetRepoPath,
  ];
}

function parseNonEmptyLines(output) {
  return output
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter((value) => value.length > 0);
}

function listRefNames(repoPath, refPrefix, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "for-each-ref", "--format=%(refname)", refPrefix], {verbose, useColor});
  return parseNonEmptyLines(result.stdout);
}

function listLocalBranches(repoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "for-each-ref", "--format=%(refname:short)", "refs/heads"], {verbose, useColor});
  return parseNonEmptyLines(result.stdout);
}

function removeRefs(repoPath, refs, options) {
  const {verbose, useColor} = options;
  let removedCount = 0;
  for (const refName of refs) {
    const result = runGit(["-C", repoPath, "update-ref", "-d", refName], {
      allowFailure: true,
      verbose,
      useColor,
    });
    if (result.status === 0) {
      removedCount++;
    }
  }
  return removedCount;
}

function removeInheritedRemoteTrackingRefs(targetRepoPath, options) {
  const {verbose, useColor} = options;
  const remoteRefs = listRefNames(targetRepoPath, "refs/remotes", options);
  const removedCount = removeRefs(targetRepoPath, remoteRefs, options);
  runGit(["-C", targetRepoPath, "symbolic-ref", "--delete", "refs/remotes/origin/HEAD"], {
    allowFailure: true,
    verbose,
    useColor,
  });
  return removedCount;
}

function removeInheritedTags(targetRepoPath, options) {
  const tagRefs = listRefNames(targetRepoPath, "refs/tags", options);
  return removeRefs(targetRepoPath, tagRefs, options);
}

function unsetLocalConfigKey(repoPath, key, options) {
  const {verbose, useColor} = options;
  runGit(["-C", repoPath, "config", "--local", "--unset-all", key], {
    allowFailure: true,
    verbose,
    useColor,
  });
}

function prepareTargetConfigForNormalizationFetch(targetRepoPath, options) {
  const {verbose, useColor} = options;

  // Ensure normalization fetch can see all heads, regardless of cloned local fetch config.
  unsetLocalConfigKey(targetRepoPath, "remote.origin.fetch", options);
  runGit(["-C", targetRepoPath, "config", "--local", "--add", "remote.origin.fetch", "+refs/heads/*:refs/remotes/origin/*"], {verbose, useColor});

  // Avoid partial-clone fetch behavior inherited from source clone during normalization.
  unsetLocalConfigKey(targetRepoPath, "remote.origin.promisor", options);
  unsetLocalConfigKey(targetRepoPath, "remote.origin.partialclonefilter", options);

  // Prevent sparse-checkout inherited config from creating unexpectedly sparse working tree.
  for (const key of SPARSE_EXCLUDED_CONFIG_KEYS) {
    unsetLocalConfigKey(targetRepoPath, key, options);
  }
}

function ensureRemoteBranchFetched(targetRepoPath, defaultBranch, options) {
  const {verbose, useColor} = options;
  const remoteRef = `refs/remotes/origin/${defaultBranch}`;
  const result = runGit(["-C", targetRepoPath, "show-ref", "--verify", "--quiet", remoteRef], {
    allowFailure: true,
    verbose,
    useColor,
  });
  if (result.status !== 0) {
    throw new UsageError(
      [
        `Missing origin/${defaultBranch} after fetch in ${targetRepoPath}.`,
        `Ensure fetch spec includes refs/heads/${defaultBranch}.`,
      ].join(" "),
    );
  }
}

function pruneLocalBranchesExceptDefault(targetRepoPath, defaultBranch, options) {
  const {verbose, useColor} = options;
  const localBranches = listLocalBranches(targetRepoPath, options);
  let removedCount = 0;
  for (const branchName of localBranches) {
    if (branchName === defaultBranch) {
      continue;
    }
    runGit(["-C", targetRepoPath, "branch", "-D", branchName], {verbose, useColor});
    removedCount++;
  }
  return removedCount;
}

function normalizeTargetToRemoteTruth(sourceRepoPath, targetRepoPath, originUrl, defaultBranch, options) {
  const {verbose, useColor} = options;

  printInfo("Setting target origin URL...", useColor);
  runGit(["-C", targetRepoPath, "remote", "set-url", "origin", originUrl], {verbose, useColor});

  printInfo("Preparing target config for normalization fetch...", useColor);
  prepareTargetConfigForNormalizationFetch(targetRepoPath, options);

  printInfo("Removing inherited remote-tracking refs...", useColor);
  const removedRemoteRefs = removeInheritedRemoteTrackingRefs(targetRepoPath, options);

  printInfo("Removing inherited tags...", useColor);
  const removedTags = removeInheritedTags(targetRepoPath, options);

  printInfo("Fetching and pruning origin...", useColor);
  runGit(["-C", targetRepoPath, "fetch", "--progress", "--prune", "origin"], {
    verbose,
    useColor,
    streamOutput: true,
  });

  ensureRemoteBranchFetched(targetRepoPath, defaultBranch, options);

  printInfo(`Checking out ${defaultBranch} and resetting to origin/${defaultBranch}...`, useColor);
  runGit(["-C", targetRepoPath, "checkout", "-B", defaultBranch, `origin/${defaultBranch}`], {verbose, useColor});
  runGit(["-C", targetRepoPath, "reset", "--hard", `origin/${defaultBranch}`], {verbose, useColor});
  runGit(["-C", targetRepoPath, "clean", "-fd"], {verbose, useColor});

  printInfo("Pruning non-default local branches...", useColor);
  const prunedLocalBranches = pruneLocalBranchesExceptDefault(targetRepoPath, defaultBranch, options);

  printInfo("Syncing final local git config from source clone...", useColor);
  const changedKeys = syncLocalConfig(sourceRepoPath, targetRepoPath, options);

  return {
    changedKeys,
    prunedLocalBranches,
    removedRemoteRefs,
    removedTags,
  };
}

function listLocalConfigEntries(repoPath, options) {
  const {verbose, useColor} = options;
  const result = runGit(["-C", repoPath, "config", "--local", "--null", "--list"], {verbose, useColor});
  return parseConfigEntries(result.stdout);
}

function parseConfigEntries(output) {
  if (output.length === 0) {
    return [];
  }

  const entries = [];
  for (const chunk of output.split("\0")) {
    if (chunk.length === 0) {
      continue;
    }

    const newlineIndex = chunk.indexOf("\n");
    if (newlineIndex >= 0) {
      const key = chunk.slice(0, newlineIndex).trim().toLowerCase();
      const value = chunk.slice(newlineIndex + 1);
      if (key.length > 0) {
        entries.push({key, value});
      }
      continue;
    }

    const equalIndex = chunk.indexOf("=");
    if (equalIndex >= 0) {
      const key = chunk.slice(0, equalIndex).trim().toLowerCase();
      const value = chunk.slice(equalIndex + 1);
      if (key.length > 0) {
        entries.push({key, value});
      }
      continue;
    }

    const key = chunk.trim().toLowerCase();
    if (key.length > 0) {
      entries.push({key, value: ""});
    }
  }
  return entries;
}

function shouldSyncConfigKey(key) {
  if (SPARSE_EXCLUDED_CONFIG_KEYS.has(key)) {
    return false;
  }
  if (STRUCTURAL_EXCLUDED_CONFIG_KEYS.has(key)) {
    return false;
  }
  return !STRUCTURAL_EXCLUDED_CONFIG_PREFIXES.some((prefix) => key.startsWith(prefix));
}

function groupEntriesByKey(entries) {
  const grouped = new Map();
  for (const {key, value} of entries) {
    if (!shouldSyncConfigKey(key)) {
      continue;
    }
    const current = grouped.get(key);
    if (current) {
      current.push(value);
    }
    else {
      grouped.set(key, [value]);
    }
  }
  return grouped;
}

function valuesEqual(left, right) {
  if (left.length !== right.length) {
    return false;
  }
  for (let i = 0; i < left.length; i++) {
    if (left[i] !== right[i]) {
      return false;
    }
  }
  return true;
}

function syncLocalConfig(sourceRepoPath, targetRepoPath, options) {
  const {verbose, useColor} = options;
  const sourceConfig = groupEntriesByKey(listLocalConfigEntries(sourceRepoPath, options));
  const targetConfig = groupEntriesByKey(listLocalConfigEntries(targetRepoPath, options));

  // Convergent sync: for each non-excluded key, target values are replaced with source values.
  // This is intended for freshly created clone targets (see ensureTargetDoesNotExist()).

  const keys = new Set([...sourceConfig.keys(), ...targetConfig.keys()]);
  const sortedKeys = [...keys].sort();
  let changedKeys = 0;

  for (const key of sortedKeys) {
    const sourceValues = sourceConfig.get(key) ?? [];
    const targetValues = targetConfig.get(key) ?? [];
    if (valuesEqual(sourceValues, targetValues)) {
      continue;
    }

    changedKeys++;
    if (targetValues.length > 0) {
      runGit(["-C", targetRepoPath, "config", "--local", "--unset-all", key], {
        allowFailure: true,
        useColor,
        verbose,
      });
    }
    for (const value of sourceValues) {
      runGit(["-C", targetRepoPath, "config", "--local", "--add", key, value], {verbose, useColor});
    }
  }

  return changedKeys;
}

function run() {
  const rawArgv = process["argv"];
  const argv = Array.isArray(rawArgv) ? rawArgv.slice(2) : [];
  const args = parseArguments(argv);
  const {sourceRepoPath, targetRepoPath, dryRun, useColor, verbose} = args;

  const gitOptions = {verbose, useColor};

  ensureDirectoryExists(sourceRepoPath, "Source path");
  ensureSourceIsGitRepository(sourceRepoPath, gitOptions);
  ensureTargetDoesNotExist(targetRepoPath);

  const originUrl = detectOriginUrl(sourceRepoPath, gitOptions);
  const defaultBranch = detectOriginDefaultBranch(sourceRepoPath, gitOptions);
  const cloneArgs = buildCloneArgs(sourceRepoPath, targetRepoPath);

  if (verbose || dryRun) {
    printKeyValue("Source", sourceRepoPath, useColor);
    printKeyValue("Target", targetRepoPath, useColor);
    printKeyValue("Origin URL", originUrl, useColor);
    printKeyValue("Default branch", defaultBranch, useColor);
    printKeyValue("Clone command", colorize(formatCommand("git", cloneArgs), ANSI.cyan, useColor), useColor);
  }

  if (dryRun) {
    const sourceConfig = groupEntriesByKey(listLocalConfigEntries(sourceRepoPath, gitOptions));
    printWarning(`Dry run: clone skipped. ${sourceConfig.size} local config keys would be mirrored after clone.`, useColor);
    printInfo("Dry run plan: set origin URL, prepare safe normalization fetch config, remove inherited refs/tags, fetch --prune origin, checkout/reset/clean default branch, prune non-default branches, then sync final local config.", useColor);
    return;
  }

  printInfo("Cloning target repository from local source...", useColor);
  runGit(cloneArgs, {...gitOptions, streamOutput: true});

  printInfo("Normalizing target repository to remote truth...", useColor);
  const normalizationStats = normalizeTargetToRemoteTruth(sourceRepoPath, targetRepoPath, originUrl, defaultBranch, gitOptions);

  printSuccess(
    `Done. Created ${targetRepoPath}. Mirrored ${normalizationStats.changedKeys} config key(s), removed ${normalizationStats.removedRemoteRefs} remote-tracking ref(s), removed ${normalizationStats.removedTags} tag(s), pruned ${normalizationStats.prunedLocalBranches} local branch(es).`,
    useColor,
  );
}

try {
  run();
}
catch (error) {
  const useColor = detectColorSupport();
  if (error instanceof UsageError) {
    printError(error.message, useColor);
    printUsage();
    process.exit(USAGE_EXIT_CODE);
  }
  if (error instanceof GitError) {
    printError(error.message, useColor);
    process.exit(COMMAND_EXIT_CODE);
  }

  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  printError(message, useColor);
  process.exit(COMMAND_EXIT_CODE);
}

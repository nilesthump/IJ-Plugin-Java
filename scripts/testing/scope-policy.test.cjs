
'use strict';
// Run explicitly for scope-policy changes: node scripts/testing/scope-policy.test.cjs
// Uses isolated repositories only; product refs/index/hooks are never modified.
const fs = require('node:fs');
const path = require('node:path');
const cp = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '../..');
const policy = JSON.parse(fs.readFileSync(path.join(root, '.github/branch-scopes.json'), 'utf8'));
const workflow = fs.readFileSync(path.join(root, '.github/workflows/scope.yml'), 'utf8');
const expression = workflow.match(/const allowed = path => ([\s\S]*?);/)[1];
const remoteAllowed = (branch, file) => new Function('policy', 'target', 'path',
  'return ' + expression)(policy, {head: {ref: branch}}, file);
const runRoot = fs.mkdtempSync(path.join(root, 'tmp/f2-scope-'));
const repo = path.join(runRoot, 'repo');
fs.mkdirSync(repo);
function git(...args) {
  return cp.execFileSync('git', args, {cwd: repo, encoding: 'utf8', stdio: ['ignore','pipe','pipe']}).trim();
}
git('init', '--initial-branch=dev-manager');
git('config', 'user.name', 'Scope test');
git('config', 'user.email', 'scope@example.invalid');
fs.writeFileSync(path.join(repo, 'README.md'), 'fixture');
git('add', '.'); git('commit', '-m', 'scope fixture');
const baseline = git('rev-parse', 'HEAD');
const cases = [
  ...policy.managerExceptions.prefixes.map(p => ['dev-manager', p+'Example.java', true]),
  ...policy.managerExceptions.files.map(p => ['dev-manager', p, true]),
  ['dev-manager','src/main/java/edu/courseassist/editor/f20/Example.java',false],
  ['dev-manager','src/main/java/edu/courseassist/ui/ChatTimeline.java.bak',false],
  ['dev-manager','src/main/java/edu/courseassist/ui/MixedDraftEditor.java',false],
  ['dev-manager','src/main/java/edu/courseassist/editor/JavaCodeCapturePort.java',false],
  ['dev-manager','src/main/java/edu/courseassist/pdf/Reader.java',false],
  ['dev-manager','src/test/resources/ui/test.txt',false],
  ['dev-manager','spec/tasks/dev-a/STATUS.md',false],
  ['dev-manager','src/main/java/edu/courseassist/core/edit/Service.java',true],
  ['dev-a','src/main/java/edu/courseassist/editor/Test.java',true],
  ['dev-a','src/main/java/edu/courseassist/ui/f2/Test.java',false],
  ['dev-b','src/main/java/edu/courseassist/pdf/Test.java',true],
  ['dev-c','src/main/java/edu/courseassist/ui/Test.java',true],
  ['dev-c','scripts/check-scope.ps1',false]
];
let results = [];
function local(branch, expected, label) {
  const result = cp.spawnSync('powershell', ['-NoProfile','-ExecutionPolicy','Bypass','-File',
    path.join(root,'scripts/check-scope.ps1'), '-Staged','-Branch',branch,
    '-RepositoryPath',repo,'-PolicyPath',path.join(root,'.github/branch-scopes.json')],
    {encoding:'utf8'});
  assert.equal(result.status === 0, expected, label+'\n'+result.stdout+'\n'+result.stderr);
  results.push({label, passed:true});
}
for (const [branch,file,expected] of cases) {
  git('read-tree',baseline);
  const absolute = path.join(repo,file); fs.mkdirSync(path.dirname(absolute), {recursive:true});
  fs.writeFileSync(absolute,'fixture\n');
  git('add','--',file);
  local(branch,expected,branch+':'+file);
  assert.equal(remoteAllowed(branch,file),expected,'trusted workflow:'+file);
}
local('main',false,'main development rejected');
git('read-tree',baseline);
const oldFile='src/main/java/edu/courseassist/ui/MixedDraftEditor.java';
git('add','--',oldFile);git('commit','-m','rename source fixture');
git('rm','--',oldFile);
const newFile='src/main/java/edu/courseassist/ui/f2/Renamed.java';
fs.mkdirSync(path.dirname(path.join(repo,newFile)), {recursive:true});
fs.writeFileSync(path.join(repo,newFile),'fixture\n');git('add','--',newFile);
local('dev-manager',false,'rename excluded source into allowed target');
assert.equal([oldFile,newFile].every(p=>remoteAllowed('dev-manager',p)),false);
assert.match(workflow,/file.filename, file.previous_filename/);
assert.match(workflow,/ref: main/);
fs.writeFileSync(path.join(runRoot,'results.json'),JSON.stringify(results,null,2));
console.log(JSON.stringify({passed:results.length, trustedParity:cases.length, evidence:path.join(runRoot,'results.json')}));

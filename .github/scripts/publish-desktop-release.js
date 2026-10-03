// Publishes desktopApp/output to a release repo and prunes old releases there.
// Called from actions/github-script: require(...)({ github, context, repo, version, label, keep }).
const fs = require('fs');
const path = require('path');

module.exports = async ({ github, context, repo, version, label, keep }) => {
  const owner = 'homebase-id';
  const outputDir = path.join(process.cwd(), 'desktopApp', 'output');

  const { data: refData } = await github.rest.git.getRef({ owner, repo, ref: 'heads/main' });
  try {
    await github.rest.git.createRef({ owner, repo, ref: `refs/tags/v${version}`, sha: refData.object.sha });
  } catch (error) {
    if (!error.message.includes('Reference already exists')) throw error;
  }

  const { data: release } = await github.rest.repos.createRelease({
    owner, repo,
    tag_name: version,
    name: version,
    body: `Desktop ${label} release v${version}\n\nBuilt from ${context.repo.owner}/${context.repo.repo}@${context.sha.substring(0, 7)}`,
  });

  for (const file of fs.readdirSync(outputDir)) {
    const filePath = path.join(outputDir, file);
    if (!fs.statSync(filePath).isFile()) continue;
    console.log(`Uploading ${file}...`);
    await github.rest.repos.uploadReleaseAsset({
      owner, repo, release_id: release.id, name: file, data: fs.readFileSync(filePath),
    });
  }
  console.log(`Release complete: ${release.html_url}`);

  // Nothing else prunes these. Safe: deltas come from the local Conveyor cache, and installed
  // apps update through releases/latest/download.
  const releases = await github.paginate(github.rest.repos.listReleases, { owner, repo, per_page: 100 });
  // Every release tags the same commit, so created_at doesn't order them.
  releases.sort((a, b) => Date.parse(b.published_at) - Date.parse(a.published_at) || b.id - a.id);
  for (const stale of releases.slice(keep)) {
    await github.rest.repos.deleteRelease({ owner, repo, release_id: stale.id });
    // The release is published under the bare version but tagged with a v prefix, so both refs exist.
    for (const tag of [stale.tag_name, `v${stale.tag_name}`]) {
      try {
        await github.rest.git.deleteRef({ owner, repo, ref: `tags/${tag}` });
      } catch (error) {
        console.log(`Could not delete tag ${tag}: ${error.message}`);
      }
    }
    console.log(`Deleted release ${stale.tag_name}`);
  }
};

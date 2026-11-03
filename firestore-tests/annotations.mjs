// A test reporter for CI: every failed test becomes a GitHub annotation, and a notice sums up the
// run, because annotations are what can be read back from the CI API.
const escape = (text) => String(text).replace(/%/g, "%25").replace(/\r/g, "%0D").replace(/\n/g, "%0A");

export default async function* annotations(source) {
  const inActions = process.env.GITHUB_ACTIONS === "true";
  let passed = 0;
  let failed = 0;
  for await (const event of source) {
    // Only single tests count; suites (describe) and whole files are reported too.
    if (event.data?.details?.type !== "test" || event.data.file === undefined || event.data.nesting === 0) continue;
    if (event.type === "test:pass") passed++;
    if (event.type === "test:fail") {
      failed++;
      const error = event.data.details.error;
      const reason = String(error?.cause?.message ?? error?.message ?? "failed").trim();
      if (inActions) yield `::error title=Firestore rules test failed::${escape(`${event.data.name}: ${reason}`)}\n`;
    }
  }
  const summary = `${passed} passed, ${failed} failed`;
  yield inActions ? `::notice title=Firestore rules tests::${summary}\n` : `Firestore rules tests: ${summary}\n`;
}

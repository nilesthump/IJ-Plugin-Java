# PDF adapter: implementation and handoff notes

Updated: 2026-10-20. Implementation class: `edu.courseassist.pdf.PdfBoxPdfReader`.

The adapter implements the existing synchronous `PdfReader` contract and has a public no-argument constructor. It keeps no shared mutable state. Core remains responsible for background execution, managed-file lifetime, cancellation, document identity, MD5 deduplication, chunking, embedding and publication.

## Extraction rules

- Only `request.managedSnapshot()` is opened. The exact request is returned as `result.source()`; source IDs and the supplied MD5 are not recalculated or rewritten.
- Every physical page produces one result, in order from 1 through the PDFBox page count. Blank and image-only pages remain `EMPTY` when extracted text is blank; there is no OCR.
- Text uses PDFBox content-stream order (`sortByPosition=false`), with LF line/page separators. The extracted string is stored directly, including leading/trailing whitespace. The adapter does not trim, reflow or concatenate pages.
- Explicit PDF page-label metadata is returned separately. Labels are not inferred from visible footer text and never replace physical page numbers. No metadata means `Optional.empty()`.
- Each page's direct content streams are decoded before text extraction. A real regression test showed that PDFBox's text pipeline otherwise logs and skips an unsupported content-stream filter, turning a damaged page into an apparent empty page. Explicit decoding propagates that `IOException` and prevents partial success. It adds one decoding pass over those streams.
- File source, document and validation streams use try-with-resources. The outer source closes even if loading fails; successful reads and later-page failures are covered by Windows rename/delete tests.
- Password-required documents propagate PDFBox's original `InvalidPasswordException`. Documents opened without a password but denying text extraction fail with an explicit `IOException`. There is no password field in the current request.

## Actual verification

Java/Javac: installed JetBrains JBR 25.0.3. Standalone compilation uses `--release 25` and UTF-8. Supplemental JUnit execution uses the declared PDFBox 3.0.5 and JUnit Platform 1.13.4/Jupiter 5.13.4, with PDFBox's runtime dependencies from Maven Central. It is separate from, and does not replace, the required unified Gradle validation.

All 12 tests in `PdfBoxPdfReaderTest` passed: three-page text/blank/text; separate labels; image-only/blank pages; managed copy after original deletion and exact source identity; whitespace/LF; repeated successful handle release; missing file; invalid/truncated file release; later-page failure without partial success; password exception and release; denied extraction and release; concurrent independent reads of one snapshot.

The required unified `scripts/validate.ps1 -Scope All` also finished with exit code 0 against the fixed IDEA 2026.2.3 / build 262.10968.63 SDK: production/test compilation, FixtureSmoke, all 88 JUnit tests in 15 suites (including these 12 PDF tests; zero failures, errors or skips), and `buildPlugin` passed. Output: `build/distributions/IJ-Plugin-Java-0.1.0-dev.zip`; raw log: `tmp/dev-b-validation/baseline-network.log`. Initial SDK download/extraction and cache construction dominated the first build. This is not an actual IDE runtime or production registration test.

Local real-material probe: nine instructor PDFs, 199 physical pages, zero empty extracted pages. Per-document page counts match an independent pypdf read. The probe copied each source into a temporary managed snapshot, checked contiguous physical numbering and exact request identity, checked that original bytes were unchanged, and renamed/deleted the managed copy after reading. Only counts/timing were logged, not lecture text. Original instructor PDFs and both original planning/assignment PDFs remain outside Git.

These checks establish file parsing, numbering, source and handle behavior on these inputs. They do not establish correct human reading order for every slide, complete glyph recovery, ideal RAG chunks, production extension loading, course-answer quality or target IDE compatibility. PDFBox remains a tolerant parser; direct page-stream validation does not turn it into a general PDF conformance validator, and malformed fonts/nested resources/layouts may have other library-specific limitations.

Local raw records: `tmp/dev-b-validation/javac.log`, `junit-final.log`, `junit/TEST-junit-jupiter.xml`, `course-pdfs.log`. Earlier failed attempts are preserved: default sandbox networking denied Gradle's socket; initial JUnit temporary-directory cleanup was denied by the sandbox and was corrected by selecting a workspace temporary directory; the later-page regression initially failed and was fixed by explicit stream validation.

## Supplemental command reproduction

From the checkout root, with the exact dependency JARs already downloaded under the ignored `tmp/dev-b-validation/lib/`:

```powershell
$jdk = 'C:\Program Files\JetBrains\DataGrip 2026.2.1\jbr'
$sources = @(
  'src/main/java/edu/courseassist/contract/CommonContracts.java',
  'src/main/java/edu/courseassist/contract/PdfContracts.java',
  'src/main/java/edu/courseassist/contract/PdfReader.java',
  'src/main/java/edu/courseassist/pdf/PdfBoxPdfReader.java',
  'src/test/java/edu/courseassist/pdf/PdfBoxPdfReaderTest.java'
)
& "$jdk/bin/javac.exe" --release 25 -encoding UTF-8 -cp 'tmp/dev-b-validation/lib/*' -d tmp/dev-b-validation/classes $sources
& "$jdk/bin/java.exe" "-Djava.io.tmpdir=$PWD\tmp\dev-b-validation\junit-temp" "-Dpdfbox.fontcache=$PWD\tmp\dev-b-validation\font-cache" -cp 'tmp/dev-b-validation/lib/*;tmp/dev-b-validation/classes' org.junit.platform.console.ConsoleLauncher execute --scan-classpath --include-package edu.courseassist.pdf --reports-dir tmp/dev-b-validation/junit --disable-ansi-colors
```

Dependency versions used: PDFBox/fontbox/pdfbox-io 3.0.5, commons-logging 1.3.5, commons-io 2.19.0, junit-platform-console-standalone 1.13.4. No JARs or downloaded SDKs belong in commits. Ordinary project verification continues to use `scripts/validate.ps1 -Scope All`; targeted Gradle feedback is `./gradlew.bat test --tests "edu.courseassist.pdf.*"`.

## Manager integration request

See [DEPENDENCY_REQUEST](DEPENDENCY_REQUEST.md). Existing extension point: `edu.courseassist.f0.pdfReader`; register exactly one implementation, `edu.courseassist.pdf.PdfBoxPdfReader`, using the existing plugin dependency and contract. The adapter needs no service injection, credentials or disposal hook. `plugin.xml` is owned by dev-manager and has not been edited here.

After registration is merged into main, verify the actual application extension resolves the reader and import the synthetic three-page file through the existing course pipeline. Confirm fixed source/physical page evidence and failure without replacing the previous ready version. Keep real model requests and semantic retrieval validation separate.

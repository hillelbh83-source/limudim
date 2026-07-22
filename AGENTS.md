# AGENTS.md

## Global Visual Design Contract (Critical)

1. The entire product must use a consistent glass-morphism design language inspired by iOS 26.
2. New and modified surfaces, sheets, popovers, cards, inputs, menus, and action buttons must use:
   - Translucent layered surfaces that preserve context behind them.
   - Soft blur or a performant visual equivalent, subtle saturation, and restrained highlights.
   - Fine semi-transparent borders, soft depth shadows, generous rounded corners, and polished press/motion feedback.
   - High-contrast content in both light and dark mode; glass styling must never reduce readability.
3. Icons must be clean, optically balanced outline icons. Do not use Unicode glyphs as production action icons when a vector icon can be used.
4. Motion must feel direct and physical: content should follow gestures, new messages should animate into place, and controls must not jump or rebuild after the first typed character.
5. Preserve the existing Courses/Search/Profile bottom navigation design unless the user explicitly requests a change to it.
6. Apply this visual contract across the web and Android implementations whenever touching a related component.

## 0) Canonical GitHub Repository and Android CI

1. The GitHub repository used for pushes and autoאנmated Android builds is:
   `https://github.com/hillelbh83-source/limudim`
2. The Actions page is:
   `https://github.com/hillelbh83-source/limudim/actions`
3. In this checkout the GitHub remote is named `github`. The `origin` remote may
   point at a separate GitLab mirror; do not assume that pushing `origin` starts
   GitHub Actions.
4. `.github/workflows/android-apk.yml` builds the local React Android bundle and
   the APK automatically on every pushed branch. Do not commit generated web
   assets or APK/AAB files.

This file explains how course content is structured in this repository and exactly which files must be updated when adding:
1. A new course.
2. A new chapter.
3. A new subchapter/section.

All paths below are relative to the repository root.

## 1) Project Content Architecture

### 1.1 Core source of truth

1. `constants.ts`
- Defines course catalog (`COURSES`, `SCHOOL_COURSES`, `ALL_COURSES`).
- Defines chapter/section metadata shown in UI (titles, section tree, category, icon).
- Some course chapter lists are manual constants; some are generated from file globs.

2. `content/<course-dir>/chapterX/*.tsx`
- Actual lesson content files.
- These are lazy-loaded by `content/ContentRegistry.tsx`.

3. `content/ContentRegistry.tsx`
- Runtime loader map for courseId -> folder + filename stem.
- If a courseId is missing here, content will not load (fallback placeholder or "course in development").

### 1.2 Runtime flow (high level)

1. `App.tsx` reads hash route (`#courseId/chapterId/sectionId`) and resolves course from `ALL_COURSES`.
2. `Sidebar` renders chapters/sections from `constants.ts` metadata.
3. `ContentRegistry` resolves the file path from course config + chapterId + sectionId.
4. It lazy-loads the matching TSX file from `content/...`.

If metadata exists but file does not exist, the user gets `ChapterPlaceholder`.

## 2) File Naming Contract (Critical)

`ContentRegistry` currently uses this resolution logic:

1. Standard section courses
- Path pattern: `content/<dir>/chapter<chapterId>/Section<chapterId>_<suffix>.tsx`
- `suffix` is section id after first dot, with dots converted to underscores.
- Example: sectionId `4.3.1` -> file `Section4_3_1.tsx`.

2. Probability course
- Stem is `Prob_Section`.
- Example: `2.10` -> `Prob_Section2_10.tsx`.

3. Lecture-style course (`intro-ee`)
- Path pattern: `content/intro-ee/chapter<chapterId>/Lecture<chapterId>.tsx`.
- Important: sectionId does not change loaded file; all sections in that chapter point to same lecture TSX.

## 3) Current Course Mapping (Runtime)

From `content/ContentRegistry.tsx`:

- `infi3` -> dir `infi3`, stem `Section`
- `job-from-the-storm` -> dir `job-from-the-storm`, stem `Section`
- `intro-ee` -> dir `intro-ee`, stem `Lecture`
- `history-bagrut` -> dir `history`, stem `Section`
- `history-exam` -> dir `history-exam`, stem `Section`
- `lashon-bagrut` -> dir `lashon`, stem `Section`
- `harmonic` -> dir `harmonic`, stem `Section`
- `infi2` -> dir `infi2`, stem `Section`
- `linear-systems` -> dir `linear-systems`, stem `Section`
- `probability` -> dir `probability`, stem `Prob_Section`
- `mans-search-for-meaning` -> dir `mans-search-for-meaning`, stem `Section`
- `civics` -> dir `civics`, stem `Section`
- `ode` -> dir `ode`, stem `Section`
- `bagrut-halacha` -> dir `bagrut-halacha`, stem `Section`
- `mechanics` -> dir `mechanics`, stem `Section`
- `groups` -> dir `groups`, stem `Section`
- `data-structures` -> dir `data-structures`, stem `Section`
- `topology` -> dir `topology`, stem `Section`
- `infi4` -> dir `infi4`, stem `Section`
- `jeremiah` -> dir `jeremiah`, stem `Section`
- `sages-and-their-wisdom` -> dir `sages-and-their-wisdom`, stem `Section`
- `complex` -> dir `complex`, stem `Section`
- `signals-and-systems` -> dir `signals-and-systems`, stem `Section`
- `machshevet` -> dir `machshevet`, stem `Section`

## 4) Which Metadata Is Manual vs Auto-Generated

### 4.1 Auto-generated chapter metadata in `constants.ts`

These are built by `buildChaptersFromGlob(...)`:

1. `INFI2_CHAPTERS`
2. `PROBABILITY_CHAPTERS`
3. `MECHANICS_CHAPTERS`

For these courses, chapter/section ids are derived from filenames. Titles are generic by default (`"Chapter X"`, `"Chapter X.Y"` style).

### 4.2 Manual chapter metadata

All other active courses use explicit arrays in `constants.ts` and require manual metadata edits when adding chapters/sections.

## 5) Required Changes by Scenario

## 5.1 Add a new section (subchapter) to an existing chapter

### Mandatory changes

1. Create the TSX file in the correct folder and naming pattern.
- Example: `content/groups/chapter3/Section3_7.tsx`

2. Update section metadata in `constants.ts` for manual-metadata courses.
- Add section id/title to the correct chapter array.
- For nested structures, add under `children` where appropriate.

### Conditional

1. For auto-generated metadata courses (`infi2`, `probability`, `mechanics`):
- You usually do not edit metadata arrays directly.
- Creating the correctly named file is enough for id discovery.
- If you need custom titles, you must replace/override auto-generation logic.

2. For `intro-ee` (`Lecture` stem):
- Multiple sidebar sections can share one lecture file.
- If the new section is only a navigation subtopic inside the same lecture, update metadata and lecture content without creating another TSX file.

### Recommended after change

1. Regenerate search index:
- `node tools/generate_search_index.cjs`

## 5.2 Add a new chapter to an existing course

### Mandatory changes

1. Create chapter folder and files.
- Example: `content/topology/chapter5/...`

2. Update `constants.ts` chapter list for manual-metadata courses.
- Add new chapter object (`id`, `title`, `sections`).

### Conditional

1. For auto-generated metadata courses (`infi2`, `probability`, `mechanics`):
- Add folder `chapterN` and correctly named files.
- Metadata is discovered automatically.

### Recommended after change

1. Regenerate search index.

## 5.3 Add a brand-new course

### Mandatory changes (minimum for runtime)

1. Create content folder and lesson files.
- `content/<new-course-dir>/chapter1/...`

2. Add chapter metadata source in `constants.ts`.
- Either manual `const <NAME>_CHAPTERS: Chapter[] = [...]`
- Or use `buildChaptersFromGlob(import.meta.glob('./content/<dir>/**/*.tsx'), '...')`

3. Add course card entry in `COURSES` or `SCHOOL_COURSES` in `constants.ts`.
- `id`, `title`, `description`, `iconId`, `isAvailable`, `chapters`, `category`

4. Add loader mapping in `content/ContentRegistry.tsx`.
- Add directory glob to `modulesByDir`.
- Add course entry to `courseConfigById` with correct `dir` and `stem`.

Without step 4, the course exists in UI but cannot load content.

## 6) Quick "Must Change" Matrix

### New section in existing manual course

Must:
1. `content/<dir>/chapterX/<file>.tsx`
2. `constants.ts`

Usually should:
1. `public/search-index/*.json` via generator script

### New section in auto-generated metadata course (`infi2`, `probability`, `mechanics`)

Must:
1. `content/<dir>/chapterX/<file>.tsx`

Usually should:
1. Regenerate search index

### New course

Must:
1. `content/<new-dir>/...`
2. `constants.ts`
3. `content/ContentRegistry.tsx`

Should (for full tooling support):
1. `components/course-editor/courseLoader.ts`
2. `components/course-editor/courseLoader.dev.ts`
3. `components/PythiChat.tsx`
4. Search index regeneration

## 7) Blocks Playground Notes

There is a blocks playground UI for trying reusable lesson blocks:

1. Frontend: `components/BlocksPlayground.tsx`
2. Route: `#builder`

Important behavior:

1. The playground is local UI only and does not write content files automatically.
2. Use it to copy snippets and compose section code with shared components.

## 8) Search Index Notes

Search index files are produced in `public/search-index` by:

- `node tools/generate_search_index.cjs`

`npm run build` also regenerates them before building.

Known caveat:

1. Index generation keys by folder name under `content/`.
2. If `courseId` differs from folder name (example pattern: alias id -> different dir), sidebar search file names can mismatch unless you handle aliasing.

## 9) Known Legacy/Non-Authoritative Files

1. `content/*/course.json` files are not runtime source of truth for navigation.
2. `content/electricity/` appears legacy; active engineering lecture course uses `content/intro-ee/`.
3. Several scripts in `tools/` contain old absolute Windows paths and are not safe as primary automation (`update_content_registry.cjs`, `fix_registry.cjs`, `generate_registry_snippets.cjs`, `generate_metadata.cjs`).
4. Avoid non-numeric section ids unless you add custom loader logic. The default resolver assumes dotted numeric ids and can map unexpected ids to `_1` fallback files.

## 9.1 Math/LaTeX String Rule (Important)

When writing math in TSX strings (for `InlineMath` / `BlockMath` or similar):

1. Prefer `String.raw` for LaTeX payloads, especially commands with backslashes like `\frac`, `\pm`, `\mathbb`, `\cdot`, `\theta`.
2. Avoid plain JS strings with single backslashes (`"\frac"` written incorrectly as `"\frac"` in source can degrade into control escapes such as `\f`).
3. For RTL text + equations, prefer block equations for final results to avoid bidi ordering glitches in inline snippets.

Prefer direct edits in `constants.ts` and `content/ContentRegistry.tsx` for reliable updates.

## 10) Validation Checklist (After Any Content Change)

1. Run local app:
- `npm run dev`

2. Open route directly:
- `#<courseId>/<chapterId>/<sectionId>`

3. Confirm:
- Sidebar shows new node.
- Section loads real content (not placeholder).
- No console import/path errors.

4. Refresh search data if needed:
- `node tools/generate_search_index.cjs`

5. Build sanity:
- `npm run build`

## 11) Practical Examples

### Example A: Add section `4.3.1` to a standard Section course

1. Add file: `content/groups/chapter4/Section4_3_1.tsx`
2. Add metadata entry in `constants.ts` chapter 4 (possibly nested children).
3. Regenerate search index.

### Example B: Add section `2.10` to probability

1. Add file: `content/probability/chapter2/Prob_Section2_10.tsx`
2. No mandatory manual metadata update (auto-glob picks it up).
3. Regenerate search index.

### Example C: Add chapter 11 to intro-ee

1. Add file: `content/intro-ee/chapter11/Lecture11.tsx`
2. Add chapter 11 metadata in `INTRO_EE_CHAPTERS` in `constants.ts`.
3. Add section ids/titles in metadata as needed (all map to `Lecture11.tsx`).

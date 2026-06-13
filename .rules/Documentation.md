# Knodeledge: Documentation Guidelines

To maintain a clean, readable, and professional codebase for Knodeledge, all project documentation must adhere to the following rules:

---

## 1. Numerical & Directory Organization
*   All root-level documentation files must begin with a numerical prefix representing their reading sequence (e.g., `1.concepts_to_know.md`, `2.system_overview.md`).
*   Related sub-component specifications must be grouped into dedicated subdirectories using numeric naming (e.g., `3.ingestion/3.1.segmenter_spec.md`).

---

## 2. Removing Stale Content
*   Any document that becomes obsolete or no longer matches the active technical vision and codebase structure must be deleted immediately. Do not keep unused design drafts or deprecated plans.

---

## 3. Keep Existing Docs Updated
*   When features or schemas are modified, rewrite and update the existing specification documents rather than letting them go stale. Preserve historical context unless it is completely obsolete.

---

## 4. Professional Document Sizing
*   Write professional, well-sized, comprehensive documents.
*   **Do not create small, fragmented files** for minor changes or small code components. Instead, group related topics into high-quality, comprehensive manuals (e.g., combining segmenting and extraction under `3.ingestion/`).

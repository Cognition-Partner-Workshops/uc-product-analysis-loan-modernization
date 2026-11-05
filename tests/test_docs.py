"""Validation tests for the documentation artifacts in this repository."""

import re
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
MARKDOWN_FILES = sorted(
    p for p in ROOT.rglob("*.md") if ".git" not in p.relative_to(ROOT).parts
)
LINK_RE = re.compile(r"\[[^\]]*\]\(([^)\s]+)[^)]*\)")

TEMPLATE_SECTIONS = {
    "templates/confluence-page-template.md": [
        "## Overview",
        "## Architecture",
        "## Key Business Rules",
        "## Data Model",
        "## Integration Points",
        "## Modernization Considerations",
        "### Dependencies",
        "### Risks",
        "### Recommended Approach",
        "## Open Questions",
    ],
    "templates/jira-story-template.md": [
        "## Epic",
        "## Story",
        "### Description",
        "### Technical Notes",
        "### Acceptance Criteria",
        "### Test Scenarios",
        "### Dependencies",
        "### Definition of Done",
    ],
}


def _ids(paths):
    return [str(p.relative_to(ROOT)) for p in paths]


@pytest.mark.parametrize(
    "directory", ["legacy-docs", "confluence-output", "jira-stories", "templates"]
)
def test_expected_directories_exist(directory):
    assert (ROOT / directory).is_dir()


@pytest.mark.parametrize("template, sections", TEMPLATE_SECTIONS.items())
def test_templates_contain_required_sections(template, sections):
    lines = (ROOT / template).read_text(encoding="utf-8").splitlines()
    headings = [line.strip() for line in lines if line.startswith("#")]
    missing = [s for s in sections if s not in headings]
    assert not missing, f"{template} is missing sections: {missing}"


@pytest.mark.parametrize("path", MARKDOWN_FILES, ids=_ids(MARKDOWN_FILES))
def test_markdown_starts_with_h1(path):
    first = next(
        (line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()),
        "",
    )
    assert first.startswith("# "), f"{path.name} must start with an H1 heading"


@pytest.mark.parametrize("path", MARKDOWN_FILES, ids=_ids(MARKDOWN_FILES))
def test_code_fences_are_balanced(path):
    fences = [
        line
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.lstrip().startswith("```")
    ]
    assert len(fences) % 2 == 0, f"{path.name} has an unclosed code fence"


@pytest.mark.parametrize("path", MARKDOWN_FILES, ids=_ids(MARKDOWN_FILES))
def test_relative_links_resolve(path):
    broken = []
    for target in LINK_RE.findall(path.read_text(encoding="utf-8")):
        if re.match(r"^[a-z][a-z0-9+.-]*:", target, re.I) or target.startswith("#"):
            continue
        file_part = target.split("#", 1)[0]
        if file_part and not (path.parent / file_part).exists():
            broken.append(target)
    assert not broken, f"{path.name} has broken relative links: {broken}"

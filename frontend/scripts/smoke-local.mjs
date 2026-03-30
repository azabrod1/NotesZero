import { chromium } from "playwright-core";

const backendUrl = process.env.SMOKE_BACKEND_URL ?? "http://127.0.0.1:8080";
const frontendUrl = process.env.SMOKE_FRONTEND_URL ?? "http://127.0.0.1:3000";
const browserCandidates = [
  process.env.SMOKE_BROWSER,
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
  "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
  "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
].filter(Boolean);

function fail(message) {
  throw new Error(message);
}

async function request(path, init) {
  const response = await fetch(`${backendUrl}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    }
  });
  if (!response.ok) {
    fail(`${init?.method ?? "GET"} ${path} failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
}

async function waitForEditorText(page, text) {
  await page.locator("[data-testid='note-editor'] [contenteditable='true']").filter({ hasText: text }).first().waitFor({
    timeout: 15000
  });
}

async function assertEditorDoesNotContain(page, text) {
  const locator = page.locator("[data-testid='note-editor'] [contenteditable='true']").filter({ hasText: text });
  if (await locator.count()) {
    fail(`Editor still showed unexpected text: ${text}`);
  }
}

function browserPath() {
  for (const candidate of browserCandidates) {
    if (candidate) {
      return candidate;
    }
  }
  fail("No local Chrome/Edge executable found for smoke test.");
}

function genericEditorJson(title, body) {
  return JSON.stringify([
    {
      type: "heading",
      props: { level: 1 },
      content: [{ type: "text", text: title, styles: {} }],
      children: []
    },
    {
      type: "heading",
      props: { level: 2 },
      content: [{ type: "text", text: "Body", styles: {} }],
      children: []
    },
    {
      type: "paragraph",
      content: [{ type: "text", text: body, styles: {} }],
      children: []
    }
  ]);
}

async function createSeedNotes() {
  const notebooks = await request("/api/v2/notebooks");
  const dogNotebook = notebooks.find((notebook) => notebook.name === "Dog notes");
  if (!dogNotebook) {
    fail("Dog notes notebook was not seeded.");
  }

  const runId = Date.now();
  const noteOneTitle = `Smoke Note A ${runId}`;
  const noteOneBody = `Booster due next Tuesday ${runId}`;
  const noteTwoTitle = `Smoke Note B ${runId}`;
  const noteTwoBody = `Evening walks resumed ${runId}`;
  const taskText = `buy dog treats ${runId}`;

  const noteOne = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: dogNotebook.id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(noteOneTitle, noteOneBody)
    })
  });

  const noteTwo = await request("/api/v2/notes", {
    method: "POST",
    body: JSON.stringify({
      notebookId: dogNotebook.id,
      noteType: "generic_note/v1",
      editorContent: genericEditorJson(noteTwoTitle, noteTwoBody)
    })
  });

  return { noteOne, noteTwo, noteOneBody, noteTwoBody, taskText };
}

async function run() {
  const { noteOne, noteTwo, noteOneBody, noteTwoBody, taskText } = await createSeedNotes();
  const browser = await chromium.launch({
    executablePath: browserPath(),
    headless: true
  });

  try {
    const page = await browser.newPage();
    let commitPayload = null;
    try {
      await page.goto(frontendUrl, { waitUntil: "networkidle" });

      await page.getByRole("button", { name: /Dog notes/ }).click();
      await page.getByTestId(`note-tab-${noteOne.id}`).click();
      await waitForEditorText(page, noteOneBody);

      const chatInput = page.getByTestId("chat-input");
      const commitResponsePromise = page.waitForResponse((response) =>
        response.url().includes("/api/v2/chat-events/commit") &&
        response.request().method() === "POST"
      );
      await chatInput.fill(`add a task to this note ${taskText}`);
      await chatInput.press("Enter");
      const commitResponse = await commitResponsePromise;
      commitPayload = await commitResponse.json();

      await waitForEditorText(page, taskText);

      await page.getByTestId(`note-tab-${noteTwo.id}`).click();
      await waitForEditorText(page, noteTwoBody);
      await assertEditorDoesNotContain(page, taskText);

      await page.getByTestId(`note-tab-${noteOne.id}`).click();
      await waitForEditorText(page, noteOneBody);
      await waitForEditorText(page, taskText);
      await assertEditorDoesNotContain(page, noteTwoBody);
    } catch (error) {
      const tabs = await page.locator("[data-testid^='note-tab-']").allInnerTexts();
      const editorText = await page.getByTestId("note-editor").textContent();
      const editorBlocks = await page.locator("[data-testid='note-editor'] [contenteditable='true']").allTextContents();
      const screenshotPath = new URL("./smoke-local-failure.png", import.meta.url).pathname.slice(1);
      await page.screenshot({ path: screenshotPath, fullPage: true });
      const commits = await page.locator("text=Routed to").allInnerTexts();
      console.error(JSON.stringify({
        tabs,
        editorText,
        editorBlocks,
        chatRoutes: commits,
        commitPayload,
        screenshot: screenshotPath
      }, null, 2));
      throw error;
    }

    console.log(JSON.stringify({
      ok: true,
      verified: [
        "note loads from backend",
        "chat mutation updates open note editor",
        "switching notes shows consistent content",
        "switching back restores the mutated note content"
      ],
      noteIds: [noteOne.id, noteTwo.id]
    }));
  } finally {
    await browser.close();
  }
}

run().catch((error) => {
  console.error(error.stack || error.message || String(error));
  process.exit(1);
});

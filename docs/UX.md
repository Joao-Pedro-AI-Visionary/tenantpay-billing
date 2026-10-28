# TenantPay Billing: UX notes

This document explains how the panel in `frontend/` applies well known UX laws, with pointers to the
components and files where each decision lives. Paths are relative to `frontend/src/`.

## Design system in one paragraph

Light theme: white cards on a very light gray canvas, Inter (self-hosted through `@fontsource/inter`,
no CDN request) with a system font fallback, and one indigo accent reserved for the primary call to
action. All colors, spacing, radii, shadows and motion durations are CSS custom properties in
`styles/tokens.css`; components never use raw hex values for semantic colors. Every text and
background pair meets WCAG AA (contrast of at least 4.5:1, documented at the top of the tokens file).
Status is always shown with text (the exact enum, for example `ACTIVE`), an icon shape and a color
(`components/StatusBadge.vue`), so it never depends on color alone. Focus is a neutral 2px ring
(`:focus-visible` in `styles.css`) that is distinct from the accent, and `prefers-reduced-motion`
sets every motion token to 0ms.

## Fitts's law

Targets are big and close to where the eye already is.

- Every button, input, select, nav link, filter chip and menu item has a minimum height of 44px
  (`--target-min` in `styles/tokens.css`; `.btn`, `.input`, `.select` in `styles.css`; `.nav-link`,
  `.menu-item`, `.user-button` in `App.vue`; `.filter` in `views/SubscriptionsView.vue`).
- Primary buttons in forms are full width (`.btn-block`), and on phones the row actions stretch to
  the full card width (`views/SubscriptionsView.vue`, `views/CustomersView.vue`).
- The "Reset demo data" action in `components/DemoBanner.vue` keeps a 44px hit area even though it
  looks like a link.

## Hick's law

Fewer choices at each step, more choices only when asked for.

- The sidebar has six destinations at most (`navItems` in `App.vue`).
- Subscribing a customer is progressive disclosure: each customer row shows a single "Subscribe"
  button; the plan select and the confirm button appear only after it is opened
  (`subscribe-open` and `.inline-panel` in `views/CustomersView.vue`).
- Canceling is a two step action ("Cancel", then "Confirm cancel" or "Keep") in
  `views/SubscriptionsView.vue`, so the destructive choice is not on screen by default.
- The dashboard has one primary button, and it follows the next onboarding step
  (`nextStep` in `views/DashboardView.vue`).

## Jakob's law

Users spend most of their time in other products, so the layout follows convention.

- Logo top left (sidebar head on desktop, top bar on mobile), account controls top right
  (`user-menu` in `App.vue`), main navigation on the left.
- On screens narrower than 1024px the sidebar becomes a hamburger drawer (`nav-toggle`), closes on
  Escape, on backdrop click and after navigation, and is `inert` while hidden so keyboard focus never
  lands on invisible links.
- Tables become stacked cards below 768px with the column name as a label (`.table-stack` in
  `styles.css`), which is the common pattern on mobile billing apps.

## Law of Proximity and Uniform Connectedness

Related things sit together and share a container.

- Every group of related content is a card (`.card` in `styles.css`): KPI cards, onboarding,
  next charges, status breakdown, each form and each list.
- Labels sit directly above their inputs with a 6px gap (`.field`), hints sit right below the input
  they describe, and errors appear next to the submit button of the same form.
- The subscribe panel is drawn inside the customer row it belongs to
  (`.inline-panel` in `views/CustomersView.vue`).

## Miller's law

Information is chunked into small groups.

- Forms are split into labeled sections with `fieldset` and `legend`: Business / Your login
  (`views/SignupView.vue`), Basics / Pricing (`views/PlansView.vue`), Contact / Tax ID
  (`views/CustomersView.vue`).
- Navigation has six items (fewer than seven).
- The dashboard shows four KPIs, four onboarding steps and four statuses.

## Doherty threshold

Feedback in well under 400ms, so the product feels responsive.

- Buttons have a pressed state (`.btn:active` scales and shifts by 1px) and a loading state with a
  spinner, `aria-busy` and a changed label ("Saving...", "Logging in...").
- Lists and KPIs show skeleton placeholders that keep the final layout while loading
  (`components/AsyncState.vue`, KPI skeletons in `views/DashboardView.vue`).
- Interaction transitions use 120 to 300ms (`--motion-fast`, `--motion-base`, `--motion-slow`).
- Successful actions confirm through toasts (`toast.ts`, `components/ToastHost.vue`).
- The demo mock API answers in about 150ms (`LATENCY_MS` in `mock/demoApi.ts`).

## Von Restorff effect

The thing that matters most is the thing that stands out.

- The indigo accent (`--accent`) is used only by `.btn-primary`, and each screen has one of them:
  "Create plan", "Create customer", "Log in" or "Enter demo", the dashboard next step.
  The confirm button inside the subscribe panel is the only primary button of that disclosed panel.
- Active navigation uses a neutral dark indicator, not the accent (`.nav-link.router-link-active`).
- Overdue is the only KPI that turns red, and only when its value is greater than zero.

## Peak-end rule

The best moment of the flow, a subscription becoming ACTIVE, is celebrated.

- When a subscription changes to `ACTIVE` (after "Simulate payment" or after a refresh that brings
  a real webhook result), `views/SubscriptionsView.vue` shows a success card with an icon animation:
  "Payment confirmed. <customer> is now ACTIVE on <plan>." The animation is disabled for users who
  prefer reduced motion.

## Zeigarnik effect and Goal-gradient effect

Unfinished tasks are remembered, and progress makes people speed up near the goal.

- The dashboard onboarding checklist (`onboarding-checklist` in `views/DashboardView.vue`) lists
  create a plan, add a customer, subscribe, first payment, with a progress bar and "2 of 4 steps
  done". The next open step is highlighted and also becomes the dashboard primary button.

## Tesler's law and Postel's law

The panel absorbs complexity and accepts what people naturally type.

- Amounts: "49", "49,9", "49.90" and "R$ 1.234,56" are all accepted (`reaisToCents` in
  `format.ts`), normalized to "49,90" style on blur, previewed live ("R$ 49,90 per month") and sent
  as integer cents. Every amount on screen is formatted as BRL (`formatCents`).
- CPF/CNPJ: digits with or without dots, dashes, slashes or spaces are accepted, formatted while
  typing (`maskDocument` in `format.ts`: 000.000.000-00 or 00.000.000/0000-00) and only the digits are
  sent to the API. The hint says whether the value looks like a CPF or a CNPJ.
- MRR is computed for the user: ACTIVE subscriptions, with yearly plans normalized to one twelfth
  per month (`mrrCents` in `views/DashboardView.vue`, `monthlyCents` in `format.ts`).
- Webhook processing is asynchronous, so the panel polls after "Simulate payment" instead of asking
  the user to refresh.

## Occam's razor and the Pareto principle

Keep only what the main job needs.

- No UI framework or icon library: icons are a small inline set (`components/AppIcon.vue`), the
  only new runtime dependency is the self-hosted font.
- The screens focus on the 20 percent of features that carry most of the daily work: create a plan,
  add a customer, subscribe, watch payments and overdue charges. Editing and deleting plans exist in
  the API but are left out of the panel.
- One shared state component (`components/AsyncState.vue`) covers loading, error, empty and data for
  every list.

## Demo mode

`npm run build:demo` (Vite `--mode demo`, read as `import.meta.env.MODE`, no env file) builds the panel
with an in-browser mock of the whole API contract (`mock/demoApi.ts`), persisted in localStorage
under `tenantpay.demo.*` and seeded with a sample tenant, "Acme Gym". The normal `npm run build`
aliases the mock to a stub (`#demo-api` in `vite.config.ts`), so no mock code or sample data ships in
it. In demo mode a banner is always rendered at the top ("Demo mode: data is mocked in your
browser") with a "Reset demo data" action, and the login screen shows the fake demo credentials with
an "Enter demo" button.

## Accessibility checklist

- Skip link to the main content, landmarks (`nav`, `main`, `header`, labeled sections).
- Keyboard: every action is a native button, link or form control; the user menu and the drawer
  close on Escape and return focus to the control that opened them.
- Live regions announce toasts, loading states and webhook progress.
- Inputs have visible labels (placeholders are examples only), `aria-invalid` on errors and
  `aria-describedby` for hints.
- Contrast of at least 4.5:1 for text, visible focus, targets of at least 44px, no horizontal page
  scroll at 390px wide (checked with Playwright at 1280x800 and 390x844).

## References (lawsofux.com)

- [Fitts's Law](https://lawsofux.com/fittss-law/)
- [Hick's Law](https://lawsofux.com/hicks-law/)
- [Jakob's Law](https://lawsofux.com/jakobs-law/)
- [Law of Proximity](https://lawsofux.com/law-of-proximity/)
- [Law of Uniform Connectedness](https://lawsofux.com/law-of-uniform-connectedness/)
- [Miller's Law](https://lawsofux.com/millers-law/)
- [Doherty Threshold](https://lawsofux.com/doherty-threshold/)
- [Von Restorff Effect](https://lawsofux.com/von-restorff-effect/)
- [Peak-End Rule](https://lawsofux.com/peak-end-rule/)
- [Zeigarnik Effect](https://lawsofux.com/zeigarnik-effect/)
- [Goal-Gradient Effect](https://lawsofux.com/goal-gradient-effect/)
- [Tesler's Law](https://lawsofux.com/teslers-law/)
- [Postel's Law](https://lawsofux.com/postels-law/)
- [Occam's Razor](https://lawsofux.com/occams-razor/)
- [Pareto Principle](https://lawsofux.com/pareto-principle/)

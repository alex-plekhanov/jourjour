# JourJour — Requirements and Architecture

JourJour is an offline Android app for travelling with. It keeps track of what a trip
costs — entries in whatever currencies were actually spent, the wallets they were paid
from, and the receipts that back them up — and it keeps the checklist of what to do
before leaving.

**Scope of this document.** It states what the app has to do and how the code is arranged
to do it. Column lists in the data model are **illustrative, never exhaustive** — the
entity classes under `data/entity/` are the source of truth. This document may lag behind
the code, so where the two disagree the code wins and the document is what needs fixing.

---

## 1. Requirements

### 1.1 Trips

A trip has a name, a start and an end date, an optional cover photo, and a set of wallets
with their opening balances. It holds no currency of its own — its wallets each have one.
It owns its entries: deleting the trip deletes them.

### 1.2 Entries

An entry is one record of money moving. Its shape is **flat**: a single row carries an
expense half and an income half, either or both filled in. A currency exchange is one
entry with both halves — 100 USD out of the cash wallet, 9 000 RUB into it.

An entry carries a category, a date and a time, a title, a place, notes, an optional
quantity, an optional receipt photo and optional coordinates. Each half carries a wallet,
an amount and a currency.

The time starts at the moment the form was opened and can be corrected. It orders the
entries of a day and decides which side of a wallet recount an entry falls on, so an
expense remembered in the evening can be put back to the morning where it belongs.

An entry can be **repeated** from its form: the form turns into one for a new entry with
the same category, title, place, notes, quantity, wallets, amounts and coordinates, timed
now. The receipt photo stays with the purchase it was taken at, and the settled amounts,
which are what the bank charged for the original operations, are not carried over.

Rules:

- The category decides what the form offers: whether an expense half, an income half, or
  a quantity field appears at all. A category that is both — a currency exchange, money
  moved between wallets — requires both halves: money that leaves one wallet has to arrive
  somewhere. "Other" is therefore an expense only; an "other income" is made by hand when
  a trip ever needs one.
- An amount may be negative: a deposit given back — for a car, a room — is written as a
  negative amount on the entry that paid it, so that the entry shows what the deposit
  came to in the end, rather than an income standing apart from its expense.
- The unit price is derived from amount and quantity. It is shown, never stored.
- The category decides the quantity everywhere, not only on the form: a quantity stored
  under a category that counts nothing is read as none — by the statistics, by the base
  currency totals and by the export — and a file bringing one is imported without it. Such
  a figure gets in when a category is edited to stop counting, or when a spreadsheet filled
  in by hand carries a 1 in every row; the form does not show it, so nobody can correct it,
  and a column adding it up would be counting the entries.
- A category offers **favourite titles** — "Breakfast", "Lunch", "Tips" — chosen with one
  tap and starred or unstarred from the entry form.
- A half names the **wallet** it moved through, and only the trip's own wallets are on
  offer. The wallet settles the currency: cash is the currency of the notes in it and
  nothing else, while a payment method that lets the bank convert opens the whole
  dictionary and starts on **whatever that wallet was last paid in** — a card is used
  where a person is standing, not where its account is held, and a card taken to Prague
  goes on being spent in koruna. Failing that the field falls back to the last currency
  used anywhere, and failing that to the wallet's own. What a wallet was last paid in is
  read off its entries rather than remembered beside it, so correcting or deleting that
  entry corrects this along with it. Before a wallet is chosen there is no currency to
  choose either.
- The last line of the wallet picker makes a new one without leaving the form: the first
  entry of a trip is often written before anybody has thought about wallets.
- A new entry opens on the wallet the trip was last paid from, and the picker offers them
  in the order they are being used.
- Currencies are offered five-most-recently-used first, then the rest by code. That
  recency is the currency's own and global: someone who deals in euros deals in euros
  wherever they go, so a first trip to a country should not start at the top of an
  alphabet. An exchange marks both of its currencies as used — which is which is not in
  question — and so does making a wallet in a currency. On the forms where a currency is
  picked rather than written in — a budget line, an exchange rate — choosing it is what
  marks it, without waiting for the form to be saved: the next picker to open should
  already have it at the top. Ordering the picker and prefilling the field are two separate questions: the
  order is global, the prefill belongs to the wallet.

### 1.3 Dictionaries

Three, all editable, all preloaded on first run:

- **Currencies** — code, name, symbol, decimal places. The preloaded set is curated: the
  currencies a trip is likely to need, all of them in circulation. It is deliberately
  neither the whole of ISO 4217 nor what `java.util.Currency` knows, since that carries
  some fifty withdrawn codes — marks, pesetas, pre-redenomination rubles — which have no
  business being offered for a trip taken now. Anything missing is added by hand.
- **Payment methods** — name, emoji, and whether the currency is **fixed**: whether a
  wallet of this method refuses anything but its own currency. Banknotes cannot be handed
  over in a currency they are not, so cash is fixed; a card lets the bank convert, so it is
  not. The flag is written the restrictive way round because that is the exception: a new
  method is assumed to behave like a card until someone says otherwise. That is the whole
  of what a payment method decides — the money itself belongs to a wallet.
- **Categories** — name, emoji, whether it is an expense, an income or both, whether it
  tracks quantity, and its favourite titles. Two of the preloaded ones count: fuel in
  litres and accommodation in nights, both being things bought by the unit and compared by
  what the unit cost.

A dictionary entry is refused deletion while anything refers to it.

### 1.4 Wallets and balances

A wallet is a payment method in one currency on one trip, with an opening balance. It is
what an entry is paid from and what a recount counts.

A wallet has a **name**, unique within the trip, and that name is what tells wallets apart:
the same payment method may join a trip as many times as it needs to, in the same currency
or not — two folds of cash in dollars are two wallets.

The form fills the name in from the payment method and the currency — "Cash (US Dollar)" —
and keeps it in step with them until a person types one of their own, after which it stops
following. A name that was never typed is stored as none at all and built again every time
it is shown, so it follows the device language; a typed one is stored as typed.

The **currency cannot be changed** once the wallet exists: entries and recounts are amounts
in it, and rewriting it would silently reinterpret every one of them. A wallet no entry
points at can be deleted instead — its recounts go with it, since a recount cannot be
deleted on its own and says nothing about a wallet that is gone.

A trip's wallets are managed on a tab of their own, which also shows what each holds now:
opening balance, plus what came in and less what went out. What moves a wallet is what the
wallet itself was charged — the amount entered where it was paid in the wallet's own
currency, and the settled amount where it was not.

A half paid in another currency whose settled amount is not in yet therefore leaves the
balance unknown, and the wallet says so instead of showing a figure: a number that is
missing a charge is worse than no number, because it looks like a total. Reconciling the
wallet is what brings the balance back.

A wallet can be **recounted** — the person enters what is actually in the pocket, and the
app records how much and when.

Only the counted amount is stored. What should have been there is recomputed from the
entries every time it is shown, cut off at the moment of the recount, so a recount that
matched at lunchtime stops matching once an entry timed before it is added afterwards —
which is the point of keeping one. An entry keeps the time the form was opened at unless
that is corrected, so writing up the day's spending in the evening leaves the lunchtime
recount alone; putting an entry's time back to the morning is what makes it count.

Where the balance is unknown there is nothing for a recount to be measured against either,
so the form says as much and records the count on its own.

Recounts accumulate; only the latest one per wallet is shown, and always with the moment it
was taken: without it a wallet reads as agreeing, or disagreeing, for ever, when it is one
reading from one evening. Opening a wallet's recount form shows the last one first: when it
was, what was counted, how far that is from the records as they stand now, and its notes.

### 1.5 Account reconciliation

A card charges its account in the wallet's own currency and the bank does not report the
amount straight away. An entry therefore leaves the settled amount blank, and a separate
screen fills them in later, in bulk, once the operations show up in the banking app. It is
reached from the wallet it is about and shows only that wallet's halves — the ones paid in
some other currency, since everything else was charged the very amount entered. A wallet
offers it only where the payment method does not fix the currency; there is nothing to
reconcile otherwise.

The settled amount is stored without a currency: it is always the wallet's, and a wallet
keeps the currency it was created with. It is also what the balance of that wallet moves
by, which is why leaving it blank leaves the balance unknown — see 1.4.

### 1.6 Receipt photos

One photo per entry, taken with the camera, written straight into the app's own storage.
It never reaches the gallery. It can be shared out, deleted, or dropped for a whole trip
at once — the button on the trip form saying how many photos there are and what they weigh,
since what it frees is the reason for pressing it; the weight is read off the files and
written the way the system writes the size of one. Photos are shrunk on capture: the switch, the size to shrink to and the quality to
encode at are all settings, the last two because a receipt is kept in order to be read back
and what that takes is the person's to decide.

The form shows the whole of it rather than a crop: a receipt is tall and narrow with the
total at its foot, which is what a crop to a wide band takes off. Small, though — so a tap
opens it over the screen to be read, where it takes a pinch or a double tap to magnify and
a drag to move about, held to its own edges so that it cannot be pushed out of sight.

### 1.7 Cover photo of a trip

One picture per trip, picked from the gallery rather than taken. It is drawn behind the
trip's card in the list and behind the top of its entries screen, faded out under the
content. The name and dates over it are set in a heavier weight and rimmed with a halo of
the surface colour, so that they hold up against whatever the picture puts behind them.

### 1.8 Coordinates

An entry may record where it happened. The location permission is asked for at the moment
of first use, not at start-up. The coordinates are shown as text and open in whatever map
app is installed.

### 1.9 Statistics

What a trip came to. Currencies are kept apart — yen cannot be added to dollars without a
rate, and the app holds no rates — and under each one the categories are listed with their
quantity, income and expense.

The foot of a currency block nets the two sides and fills in only the one it came out on:
spending a thousand and receiving a hundred is being nine hundred down, not both at once.
The quantities are not totalled at all — litres and nights have nothing in common.

Categories keep the order of the dictionary, the one they are listed in everywhere else.
Sorting them by amount would put the same category in a different place in every currency
block, and the blocks could then no longer be read against each other.

A currency exchange is an expense in one currency and an income in another, so it appears
in both groups. Its quantity, if it has one, is counted once, under the currency the money
left in.

The tab is in **two named halves**, each under a heading a shade deeper than the currency
headings inside it, so that the hierarchy reads at a glance: these two say what a run of
blocks is, the ones under them say which currency a block is.

The first is the breakdown above, every currency on its own. **Under it stands the trip as
one figure**, everything it cost converted to the base currency through the rates the trip
carries. It is the same table read the same way — the categories, then one netted total —
and it answers a different question from the blocks above it. Those say what happened and
are exact; this one says what the trip came to and is only ever as good as the rates a
person chose to reckon in. A reading comes after the thing it reads, which is why it sits at
the foot rather than replacing what it was made from.

It is left out where it would say nothing: a trip spent in the base currency alone, where
the single block above already carries the same figures. Where no base currency has been
chosen at all, the section is a single line saying so and linking to the settings — muted
rather than red, since nothing is wrong: it is an option nobody has taken up, and the
absence of a section explains nothing to somebody looking for the total of their trip.

**Exchanges do not count towards it, categories and all.** An exchange moves money between a
person's own pockets; counted here it would add the trip's own float to what the trip cost,
and counted on both halves it would do so twice. The categories go with it — a column of
zeroes is not information. The blocks above go on showing them, because there the question
is what happened to each currency.

**Which figure gets converted** is the settled amount where there is one — that is what the
account was actually charged, the entered amount being what the price tag said. But a
settled amount is in the wallet's currency, and where that one has no rate while the entered
one does, the entered figure is converted instead: it answers the question well enough,
better at any rate than refusing to answer it. The base currency itself never needs a rate.

**The third section is a pie**, the expense categories as shares of one circle, and it is a
section of its own rather than a picture hung under the converted table: it says the same
thing as the figures in a way figures cannot, which makes it a third thing to read.

It is drawn in two cases and no others. Where the totals have been brought to the base
currency, those are what the shares are of. Where everything the trip cost was spent in one
currency, that currency's own figures already are that, and they are comparable as they
stand — so a lira-only trip gets its circle even while the rate to the base is missing. What
counts as one currency is where the money actually went, not how many blocks are on screen:
a trip that changed dollars into lira has a dollar block holding nothing but the exchange,
and an exchange is not a thing the trip cost. A trip that really did spend in several
currencies with no rates between them has no whole for a circle to divide — baht and lari
cannot be added, and a pie of them would be a picture of an arithmetic mistake.

Expenses only: a pie divides a whole into its parts, and expenses and incomes are not parts
of one whole — netting them would make a category that was paid and refunded vanish, and
setting them side by side would draw a circle that totals nothing. What came in stays in the
tables above, where it can be read as itself.

The slices go largest first, unlike the tables, which keep the dictionary's order: a table
is read to look a category up, a chart to see which one is the big one, and that is a glance
only if the big one starts at twelve o'clock. The colours are generated — evenly spaced
round the wheel from the app's own blue, at one saturation and one brightness — rather than
listed, since the number of categories a trip uses is not known in advance and a fixed list
would either run out or give two neighbours the same colour. Mid-toned, so the one palette
carries on a light ground and a dark one.

**One missing rate stops the whole section.** A part-total would read as a total, so instead
of figures there is a line saying which currencies have no rate — only those with no way
round one — ending in a link to the trip form, where rates are entered. Nothing is checked
before this point: the trip form deliberately does not mind an incomplete set of rates,
because here is where an incomplete set does harm and here is where it is reported.
Settings and shared by every trip. It is a code out of the dictionary and may be left
unset, which is how it starts. On its own it changes nothing: it says which currency a
trip's totals would be worth converting to, and the rates that make the conversion possible
belong to the trip, so a trip that carries none goes on being reported currency by currency
and the statistics simply leave the converted section out.

**The rates of a trip** live on the trip's own form, above the wallets and set up the same
way. Each is an equation — so many units of one currency, and what they come to in the base
one — and the units are there for the currencies where one buys almost nothing: 1000 LAK =
3.8 RUB says more than 1 LAK = 0.004 RUB, and holds more of the figure besides. A rate is
kept to three decimals whatever the two currencies are written in; rounding it to the two a
dollar uses would turn 1.8 into 2.

A rate is explicitly not a fact. It is whatever a person decides to reckon the trip in — an
official rate, what the exchange office round the corner was selling at, an average over the
fortnight, or a guess at what next month will bring — and the form says as much, behind an
information button rather than in a paragraph standing on the form for good.

Both ends of a rate are written down, not just the one it converts from. The base currency
is a setting and a setting can be changed; a rate that only said "to the base" would quietly
come to mean something else the day it was. Rates whose far end is no longer the base are
left exactly as they are and the form names them, which is the only thing that can be done
without changing figures nobody asked to change.

Nothing checks that the rates are complete. A currency spent with no rate against it is a
question for the report, which is where it does harm, not for the form, which is where it
would only nag. What the form does insist on is a base currency: with none set there is
nothing to convert to, so the button to add a rate is replaced by a line saying where to set
one.

A rate carries a **note** — where the figure came from, what it is really worth, the fact
that it is a guess. It is shown under the equation on the trip's form, set as a wallet's
opening balance is, so that what a rate is can be read without opening it.

A rate may also be **fetched from the internet** — the one thing in the app that goes
online. The pair and the trip's start date go out and a number comes back: the date where
that day has been and gone, the latest published rate where the trip is still ahead, since
nobody publishes a rate for a day that has not happened. The reply says which day it
actually belongs to, which is often not the day that was asked about — the source publishes
once a day on working days — and that day is **written into the note**, where it stays with
the rate instead of vanishing with the sheet. Unless the note is already somebody's own: a
note that was typed belongs to the person who typed it, so a lookup leaves it alone and
says the date under the button instead, as it did before there was a note at all. Emptying
the field hands it back, and the next lookup stamps it again. A lookup that fails says so
and changes nothing — typing a rate by hand is the normal way to fill the field, not the
fallback.

### 1.10 Export and import

A trip exports through the system document picker, in three shapes: its entries as a CSV
table, the same table as an XLSX spreadsheet, and its receipt photos as one ZIP archive.
The archive is offered only when there is at least one photo to put in it.

The spreadsheet asks first which columns to carry, and remembers the answer: next time the
same ones are ticked. The CSV is not asked — an import reads it back and has to find the
whole entry there.

The two do not carry quite the same columns. A CSV names the **wallet** of each half and
leaves the payment method out: the wallet already names one, and an import has to find a
wallet rather than guess at a pair. A spreadsheet is read by people rather than by this
app, so it may carry both.

A CSV written this way reads back in. The entries are added to the trip rather than
replacing what is there, and the file has to be one this app wrote: the column titles are
checked before anything else. Nothing is written unless every row made sense.

The **budget** goes out and comes back the same way, from the menu of its own tab. All
seven columns: category, name, notes, price, quantity, total, currency. The price is
written although it is not stored — a line arrived at as so many at so much is unreadable
in a spreadsheet without it — and a row read back may use either figure: the total as
written, or the price times the quantity where the total is blank, which is how a budget
filled in by hand says it. A file has to carry the category, the name, the total and the
currency, although the name itself may be blank; a category that brings money
in rather than spending it is refused by name, since nothing is budgeted under one.

Reading one in merges rather than adds: a line of the same name under the same category is
the line already there and is passed over, a nameless line matching the nameless line of
its category. A budget names a thing once — "Accommodation — Hotel" is written for the trip
rather than for each hotel — so that pair is what tells two lines apart, and the name is
therefore a column the file has to carry even where it says nothing. What is already on the
screen is what stays: the figures in the file do not overwrite it.

### 1.11 Dumps

The whole database and its photos can go out as one archive and come back in, from the
menu of the trip list. It is a tool for moving what was entered on one install to another while the
app is being developed — a debug build's data cannot be reached any other way once a
release build replaces it — and it stays off the menu until a setting turns it on.

A dump carries the schema version it was taken from. The version is a **ceiling, not a
match**: a file from an older schema is read as it is, and only one from a newer build is
refused, since it may name a column this build has never heard of. Every schema change so
far has only added — a table, a column with a default — so the fields an old file carries
still mean what they meant and the ones it lacks take their defaults. The first change that
renames or drops something needs either a migration for dumps or a floor below which they
are refused; until then there is no such floor.

Reading one in adds and never changes: a currency is known by its code, a
payment method or a category by its resource key or, failing that, its name, and a trip
by its name and dates; what is already there is left alone, and a trip that is already
there is skipped whole. A wallet goes with its trip along with its recounts; a rate and a
checklist item go with theirs as the plain text they are; a budget line and what was
settled about a category name their category the way an entry does; and an entry points at
its wallet by name, or by payment method and currency where the wallet has no name of its
own. The photos travel in the same archive, laid out as they are on the
device — `receipts/` and `trip_covers/` beside the document — and a row keeps its photo
only where the file came along with it. Which photos are wanted is known only once the rows
are in, so the archive is read twice and the pictures are written after them: a trip row can
reach the screen before its cover is on disk. When the files land the folder says so, and
covers already on screen that found nothing look again.

### 1.12 Localisation

English and Russian, including the preloaded dictionary contents, which follow the device
language rather than the language they were seeded in.

### 1.13 The budget

What a trip is expected to cost, written before anything is booked. A **rough plan, not a
ceiling**: three tickets at sixty thousand, ten nights at fifty dollars. A line names a
category, what it is called, how many and how much, in whatever currency the thing is
priced in.

Of price, quantity and total only two are ever independent, so the total and the quantity
are what is stored and the price is worked out from them, as it is for an entry. All three
are typed into on the form and the third follows the other two, because a person arrives at
a line either way round — so many at so much, or so much for the lot.

A quantity here is free of the category's `track_quantity`, which governs the entry form
alone: the app does not count nights when the money is spent, but ten of them is exactly
how the sum was arrived at when it was planned.

Only what a person spends on can be budgeted — categories that are expense-only. An
exchange moves money between a person's own pockets, and a category that only brings money
in has nothing to plan.

The lines add up per currency, and into one figure through the trip's own rates and the
base currency — the same bargain the statistics make, and the reason the rates exist.
Where a currency has no rate, the figure is withheld and the currency named.

Two flags belong to a **category** of a trip's budget rather than to a line, because
spending is matched to a budget by category and nothing ties a line to the entries that
paid for it; half-stamped categories would leave the app deciding what they meant.
**Spending closed** says the money side of a category is finished, whatever was actually
spent against what was planned, so nothing is left to spend there. **Planning closed** says
the detailed plan for it is complete and may be read as the whole of what is still to come.
The second waits on the schedule; the row it lives in is written down now. A row exists
only while it says something: both flags off is the same as no row, and the last line of a
category takes its row with it.

What closing a category does to the three columns takes a paragraph to say, so the
paragraph is behind an **i** beside the switch, the way the one about exchange rates sits
beside their heading: read once and then never again, and not worth the room it would take
on the screen for good.

The same thing can be said of a whole trip at once — the tab's menu closes every category
of the budget — which is what somebody does when they are home and the last card statement
has come in. It asks first, being one tap over a screenful of categories where the way back
is category by category, and it goes out of reach once there is nothing left open.

The tab is read in two sections, the way the statistics are: **the budget as written**,
category by category and currency by currency, and then **the base currency**, where what
was written is read against what has gone. The exact figures first, the reading of them
after.

Under its lines, a category says what it comes to: **budget** and **spent**, each a run of
per-currency amounts joined by commas the way a day's takings are, and neither converted —
these are the figures as entered. A side with nothing on it is left out rather than shown
empty. A category whose spending is closed says so in a line of its own above them.

**The base currency section** is the one part of the budget that needs the trip's rates: a
line priced in dollars and a receipt in baht are the same money to a person and nothing to
arithmetic until a rate says otherwise. Three columns, category by category:

- **plan** — the lines of the category added up while its planning is open, and what the schedule has booked once that is closed or the budget never had a line for it at all. Blank where nobody planned for it either way;
- **actual** — what its entries came to, net of anything that came back. A category that
  only brings money in — a refund, cashback — reads as a negative figure, which is what it
  does to what the trip has cost. Exchanges are out of it, as they are everywhere;
- **left** — what was planned and has not gone yet, never less than nothing: money spent
  over a plan is not money owed back to it. Nil for a closed category, whatever the other
  two columns came to, and blank where nothing was planned at all.

Where the rates or the base currency are missing, this section says so in the same two
sentences the statistics use, with the same link to the trip form or to the settings — and
says it once, being the only part of the tab that needs them.

Every category with either side to it is a row, including one that was never budgeted for
and has had money go on it anyway — that being the more worth seeing of the two. The total
line adds the three columns as they stand, so an unplanned expense counts against what the
trip has cost without touching what is left of the plan.

Under the totals, one figure more: **the budget including what is spent** — the actual plus
what is left, which is what the trip will have cost if the rest of it goes to plan. It is
not the plan over again once anything has happened: overspending has raised it and will not
be given back, a category closed with money still in it has lowered it, and an expense
nobody planned for has raised it too.

**The converter** hangs off the budget's menu for want of a better place — it belongs to no
one tab, and this is the tab about money in more than one currency. So much of one currency
into another, through the trip's own rates: two amounts and two currencies, all four typed
into or chosen, and whichever is touched the far amount follows. Choosing a currency leaves
the amount beside it alone, since the figure just typed is the one that was meant.

What it offers is what the trip has rates for and the base currency they point at, and
nothing else: there is no other road between two currencies here. It opens on the currency
last used against the base one, and it says at the top whose rates these are — figures a
person entered for their own reckoning, not the money changer's. Where there is no base
currency, or no rate at all, it says which of the two is missing and where to go about it.

### 1.14 Checklists

Every trip has one: the things to do before leaving. An item is a line of text in a
section, and a section is a line of text too — there is no row for one anywhere. Renaming a
section rewrites the name on every item carrying it, and deleting one deletes them; a
section whose last item goes has stopped existing, which is what deleting that item meant.
Sections are read in the order they were made, and an item joins the end of its own section
rather than the end of the list: a checklist is read in the order somebody thought of it,
and the alphabet would scatter "passport, visa, insurance" into three unrelated places.

An item carries two flags rather than a tick with three positions:

- **done** — struck through, and that is all it says;
- **not this trip** — greyed, and the tick beside it out of reach. The item stays on the
  list, which is the point: the list is meant to be the same list next time, and an item
  ruled out in March is still waiting in August. It says nothing about whether the thing was
  ever done, only that nobody is asking now.

A section whose items are all done or ruled out is settled, and is struck through the same
way. Settled sections are folded away when the screen opens — what somebody opening a
checklist wants is what is left — but a section that settles while being looked at stays
open, since shutting it would look like the screen snatching the list away at the moment of
finishing it.

Everything the list can be asked is one tap, because it is read from a doorway with one
hand full: the tick settles the item, the rest of the row opens it, and the heading folds
the section. What a whole section can be asked is behind the three dots beside its name —
renaming it, deleting it, and **ticking or unticking everything in it**, which is what
somebody does with the bag already packed and the list being caught up with afterwards.
Items ruled out for this trip are left alone by that, their tick meaning nothing. A section
has nothing of its own but its name; items are added and changed through a form where the
section is picked from a list with a last line for a new one, the way a wallet is. A
section invented there exists only on the form until the item is saved, so backing out
takes it with it.

Nothing about a checklist is required and nothing is checked: a trip may have none.

**A checklist can be copied off a trip already made**, which is the point of keeping one at
all — the same list, trip after trip, minus the ticks. The form asks three things, each
narrowing the one above it and each appearing only once the one above is answered: which
trip, then one of its sections, then one item of that section. Only the trip is required;
the other two start on "All sections" and "All items" and are usually left there, which is
what copying a list means. That is written out in the field rather than left as an empty
one needing a hint underneath: it is the ordinary answer, not the absence of one.

What comes across is merged rather than replacing anything. An item already on the list is
passed over — the same wording in the same section, neither minding case, since "Passport"
and "passport" are the same thing to whoever wrote them. The form reports both numbers
afterwards, because a copy that added nothing is not the same as one that went wrong.

**Nothing arrives done.** What was packed for the last trip is exactly what has not been
packed for this one, and carrying the ticks across would hand over a list claiming the work
was finished. **Ruled out does arrive**: that is a standing judgement about the item — no
swimming things in November — and a list copied without it would ask the same questions
again every time.

**A checklist also goes out and comes back as a CSV**, off the same menu, so that a list
can be built in a spreadsheet or carried to a phone that has never held this app. All four
columns are written every time — a table stripped of its flags would come back as a list
nobody had thought about — and the flags are written as words rather than TRUE and FALSE,
because the file is meant to be edited by hand.

Reading one back follows the entries import: the columns are found by their titles in the
first line, in whatever order they come, and only the section and the wording have to be
there. A file of two columns is a perfectly good checklist, all of it outstanding. A flag is
read as the two words the export writes it with — the same string resources, so that the
two halves of the round trip cannot drift apart — or as any of the marks a person editing
by hand reaches for: a cross, a tick, a one. Those are marks rather than words on purpose,
since they hold whatever language the column is headed in. A flag nobody can read stops the
import rather than counting as "no", a column that has not been understood being no column
to guess at. Nothing is written until the whole file has been understood; one bad row stops
the lot, with its line number.

What arrives is **merged exactly as a copy is**: an item the list already carries is passed
over, matched on section and wording with case ignored. The difference is the ticks — a
copy drops them, a file keeps them, because somebody wrote them in that column on purpose.

### 1.15 The schedule

What happens and when: a flight, a hotel booked from the fourth, a table at eight. It is
read day by day, every day of the trip whether anything is in it or not, and nothing may
be put outside the trip's own dates.

**An event is a moment, not a wall clock — and this is the decision the rest hangs off.**
An entry's time is local and stays local: the money went out on the third at noon, and no
zone the phone is later in changes which day it was spent on. An event cannot be written
down that way. A flight leaves Moscow at one in the afternoon and lands in New York at
four the same afternoon, having flown for ten hours; a pair of local date-times says it
ended before it began. So the row holds the instant it starts and how many minutes it
lasts, and every clock a person reads is that instant rendered somewhere.

What that buys is everything awkward about time, for nothing. **A day is not twenty-four
hours** — it runs from one local midnight to the next, and going west makes it thirty-one
while going east makes it seventeen, which is the honest answer to how long that day was.
The weekend the clocks change is twenty-three or twenty-five by the same arithmetic, with
no rule written for it. Two events can be compared, ordered, and found to overlap without
asking where either of them is.

**Zones are stated where somebody would think to mention one and inherited everywhere
else.** An event may declare a zone at either end: at its end it means "this is what took
us there", which is a flight; at its start, "from here on we are in X", for a move no
event accounts for. Either holds until the next event that says otherwise. Every other
event, which is most of them, declares neither and takes the zone it finds — dinner does
not need to be told it is in Bangkok if the flight before it landed there.

On the form each zone stands in the row of the end it belongs to, beside the date and the
hour it is read with, and says **"unchanged"** while the event declares nothing — which is
the truth, and which is also the first thing the picker offers, in brackets and in the
accent colour the way the wallets offer a new wallet: it is not one of the places in the
list but the answer to a different question. Declaring one is picking a place; taking it
back is picking "unchanged" again, rather than clearing a field or finding a button
somewhere else. Of the three fields on the line the date and the hour are given the width
of what they hold, since a date is eight characters for the life of the app, and the zone
takes the rest — it holds a place name and cannot know how long one is.

Before anything has moved the clocks they stand in the **home zone**, which is a setting.
Not the phone's own zone, which says where a person is *now* — halfway through a trip,
the wrong end of it — though the phone answers for it until somebody has said otherwise.

Changing it is **asked about first** wherever there is a schedule to rewrite. It rewrites
every trip in the app and shows nothing for it — that is the whole point, every hour stays
where it was — and a setting that quietly rewrote everything and looked like it had done
nothing is not something to find out about afterwards. Where there is no schedule anywhere
there is nothing to ask.

**What a person typed is the wall clock, and that is what must not move.** The moment is
what is stored, because nothing else can put two events in order; but nobody enters a
moment, they enter "dinner at eight". Write a flight in front of that dinner and the
clocks after it read four hours later: dinner is still at eight, and its moment is four
hours earlier than it was. So every write that can move the zones — an event that declares
one being added, retimed, re-zoned or deleted, and the home zone itself changing — reads
the wall clocks as they stand, applies the change, lays the zones out again, and gives
every event back the clock it had. One transaction, or the trip is left half in one zone.

That re-anchoring is why the moment can be stored at all. **Storing the wall clock instead
does not work**, and the reason is ordering: a flight leaving at 23:00 and landing at 18:00
the same evening comes before a dinner at 20:00 that night, and no sort of the clocks can
say so. Nor can the order be found by walking forward greedily, since the dinner reads
earlier than the flight in the zone the walk starts in. It would take a third column
saying which leg of the trip an event belongs to — hidden state that a person would have
to understand the day something looked wrong.

**A day is read as an unbroken column** where every minute belongs to something. An event,
a gap, or minutes claimed twice. Nothing is drawn to scale: an hour and a fortnight take
the same room, because a schedule is read as a list of what happens next and not as a
chart of how long it takes. What the bands say instead is what touches what — two events
flush against each other, three hours free between them, a clash.

The day headings are the deeper of the two bands a trip's screens are read by — the same
one the budget heads its sections with — and the blocks under them the lighter one. What
separates what is booked from what is free is not the colour but the **shadow**: an event
stands a little off the page and empty time *is* the page, so the column reads as a run of
raised things with the free hours between them. A conflict is raised with them, square
against both events that claim it, so that the three read as one run rather than as two
blocks with a hole between them.

**A day carries a line saying what it is about** — "flight to Morocco", "Istanbul", the
name of wherever this day happens — written beside its date and set more quietly than it,
the date being what the heading is found by. It belongs to the *date* and to nothing
booked on it, which is why it is written by tapping the heading and not from any form: a
day with nothing in it has no form to open, and those are exactly the days most worth
naming while a trip is being planned. Where the line runs past one row the band grows
downwards and the date stays where it was — a date that drifted down the band would be a
date nobody could scan a column of. A day with nothing to say keeps no row at all rather
than an empty one, so emptying the field is how a description is taken back. Reading is
not writing here either: the line is read off the heading while a schedule is being read,
and written where everything else about the day is.

An event is two lines tall at the least and **built downwards in the order it is read**:
the name first, then what the notes say about it, then what it is expected to cost. When
it begins and when it ends stand down the left against the whole of it — notes, money and
all, since the end of an event is the end of *it* and a clock that stopped where the name
did would be pointing at the name — and how long it lasts at the top right.

Only the length stands beside the name, so the name has the width of the block up to it.
The money would have narrowed every name on the screen for the sake of the few events that
carry any, which is the wrong way round: it takes a line of its own at the foot instead,
in the corner, where a total belongs and where nothing is squeezed by it. The notes go
between the two, set in to the name because they are the same thing continued, and running
the full width because nothing stands to the right of them. So the block grows with what is
actually in it: a name fills the room it needs, notes add to it, money adds to it again,
and an event that is none of those things is two lines and done.

Nothing is said twice — the date is in the heading above.

The figures are set quietly and the marks beside them quieter still, because they are read
at a glance and the name is what is actually looked at. A length is written as a clock is
— `01:30`, `11:00`, `168:00` for a week — and padded for the same reason the hours are, so
that a column of them can be compared without reading any of it. Hours run past
twenty-four rather than turning into days: a length is one number, and two units would be
two things to line up instead of one.

A name too long for one line grows the block from the middle, the two clocks staying
against the edges they belong to. The end an event does not have in this day is left out
altogether, mark and all — an empty line says less than none.

Where the clocks moved under it, the hour carries how far by, set as a ticket sets the day
it lands on: a small figure raised off the hour rather than a second thing beside it. It
belongs to that hour and has no business widening the column and shunting the name along,
which is also why the column is a fixed width.

A gap is one line: what to do with it on the left, and between two events how much of it
there is on the right, level with the lengths above and below. Three hours free is the
useful number; the stretches at either end of a day are the night, and how long the night
is helps nobody. An event that runs past midnight is drawn under both headings, cut square
along the join and rounded only on the outside, its length given in full in both — but
what it is expected to cost appears once, on the piece that ends it. An event costs what
it costs however many days it is spread over, and the same figure under each heading it
crossed would read as that much a night.

**Two events that touch are joined by a chain**, which is the difference between a
coincidence and a consequence: the bags come off *this* flight, and if the flight moves
they move with it. A pale chain is the coincidence and a solid one the consequence, and
the tap is where that is said, there being nowhere else to say it. It lies *across* the
join, half over each block, rather than taking a line of its own — two events that touch
should be as close together as any other pair, and a row between them would say they were
not. Where the join falls on midnight the chain is drawn twice, above and below the day's
heading, since neither day can show a join that happened in the other; both are the same
link and either tap settles it.

Drawing it belongs to the block **below** the join, which is the only order that works: a
list paints its lines in turn, so a mark hanging downwards out of one would be painted
over by whatever came next.

**Clashes are allowed and drawn rather than refused.** Two things booked over the same
minutes is a fact about a plan, not a mistake to be blocked — and the offer to fix it is
better made once the clash can be seen. So the minutes claimed twice are a band of their
own between the two events that claim them, square against both, in the colours a
destructive action is given. It opens nothing: which of the two is wrong is not a question
it can answer, and either is one tap away above and below.

**Making room** is the offer that goes with it: whatever is sat upon moves forward exactly
far enough to be clear and no further, so an empty afternoon takes up as much of the push
as it is wide and the evening often does not move at all. Nothing is ever pushed backwards
— clearing the way behind would mean shoving the morning into yesterday — so a clash with
something *earlier* is stated and not offered a way out. Nor is a push that would carry
anything off the end of the trip: that is a worse plan than the clash it fixed.

It is one transaction: half a shift is a schedule nobody asked for, and nothing is written
until Save, which is what makes cancelling free. **Keeping the links is not part of the
offer.** An event tied to another has no time of its own, so that much is written whatever
a person decides about the clash.

The form says only what *this* edit runs into — the event itself and whatever its links
dragged along. A clash elsewhere in the trip was there before the form was opened and will
be there after it is shut, and naming it here would be blaming somebody for what they are
not doing.

It is said under the hours it is about, in a block of the colours the schedule gives
minutes claimed twice. Not at the foot of the form: a form this long has a foot below the
fold, and a warning nobody scrolls to is a warning nobody gets.

And it is asked of **the times alone**, not of a finished form. An event still waiting for
its name runs into exactly what it will run into once the name is typed, and an event
being written into a gap has no name for as long as somebody is deciding whether it fits —
which is precisely when the answer is wanted.

**An event can be linked to the one before it**, which is what "and then straight to" means:
it begins where that one ends and goes wherever it goes. The start is still stored — a
derived one would vanish with its predecessor and could not be sorted or grouped by — and
the link is a promise kept on every write. There is no list to pick the predecessor from,
because there is nothing to pick: somebody writing "collect the bags" at twenty past five
means the flight that landed at ten past. So the form names the one thing near enough to
have been meant, with the hours it takes up, and offers to tie to it — and where nothing
is near enough it does not ask, "tie this to nothing" being no question. Near enough is
the same day, or the day before: a flight that landed at half past eleven last night and
the bags collected at ten past midnight are one thing, and a lunch three days ago is not
the predecessor of anything. What the event is already tied to is always named, whatever
the dates say, or a link made yesterday could not be undone today.

**An event can be repeated from its form**, the way an entry can: the form turns into one
for a new event carrying the same name, notes, hours, zones and planned cost, and saving it
writes a second event rather than changing the first. The link is the one thing not carried
over — it is what holds a start where it is, and a copy is made in order to be put
somewhere else. The hours stay as they were, so the copy lands on top of what it came from
and the form says so at once, in the block it says every clash in. That is the point of
leaving them: what a copy needs is a time of its own, and what it runs into is on the
screen while that time is being typed.

What ties and unties is the same chain the list draws over a join, as a round button:
filled while the two are tied and hollow while they are free. Two opposite words in the
same place — "tie", "untie" — are read as a label half the time and as an offer the other
half, and the mark that already means this elsewhere has neither problem.

The section is headed, with the paragraph that says what tying *does* behind an "i" beside
the heading: that a start becomes the other event's end and follows it from then on is the
whole of the feature and none of it is visible in a chain. The hours come before the name
in the line under it, because the hours are what say whether this is the thing meant — a
name three days away and a name ten minutes away read alike. They are written as the list
writes them, one over the other against the same two marks, so that what is named here is
recognised as what stands up there — but without the zone, which is the list's business
and not this line's.

The money is headed the same way and for the same reason: what a planned expense is *for*
is that the budget reads it as the finer plan for its category, and nothing on the form
says so.

**The form of an event is a screen and not a panel.** It asks eight questions and two of
them are rows of three fields; a sheet rising from the bottom over the list would put that
behind a keyboard on the first tap. So it takes the screen, with Delete in the top bar
away from the button people came to press, as every other form in the app has it.

**A length is one of three figures read three ways**, so it is picked the way the other
two are: the field is a clock — `00:30` — standing under the two clocks it is measured
between and of their width, and tapping it opens the same dial they open. One of the three
opening something that looked nothing like the other two would say they were different
kinds of thing. The dial is the twenty-four hour one whatever the phone is set to, there
being no morning in an hour, and the dialog is headed with what is being asked for rather
than with "select time".

A dial shows hours, and hours stop at twenty-three: a length set from it cannot pass 23:59.
Longer events are said the way they are meant anyway — a hotel booked from the fourth to
the seventh is two dates and two hours, not a hundred and sixty-eight.

**Tapping empty time is how an event is added**, and the tap is the whole of the question.
The settings say how long — an hour, until somebody says otherwise — and the gap says
which end of itself the hour is measured from. A gap after something runs forwards from
it: what is being written down is what happens next. The gap before the first event of a
day runs backwards from that instead, since what goes there is what comes immediately
before — getting to the airport, not the small hours. An empty day has no event to run
from, so it runs from the hour a day is taken to begin at. Either way the hour is trimmed
where the gap is shorter, and the form opens with the times already right.

**An event is moved by picking it up and putting it down**, which asks a different
question from the form: not "when exactly" but "after that one rather than this one". It
is held before it moves — a list is scrolled with the same finger, and an event that slid
out of place on a flick would be unusable — and the whole chain goes with it, since an
event tied to another has no time of its own and a flight that moved without its bags
would be saying the opposite of what the link says.

Empty time has two ends and they mean opposite things. Against the top of a gap the event
begins where the thing above it ended; against the bottom it ends where the thing below it
begins. The length it had is the length it keeps either way: what is being said is where
it goes, not how long it takes. The stretch before the first event of a day has only its
lower end to line up against and the stretch after the last only its upper, and an end
with nothing to line up against is no target at all. A day with nothing in it takes the
event at the hour it already has — what is being said there is which day, not which hour.

Minutes claimed twice are a target too, and dropping on them settles which of the two
gives way: the upper one goes in front of the lower, the lower goes straight behind the
upper. While a drag is aimed at that band it says where the drag would land instead of
what is wrong with the schedule, one thing at a time.

**Nothing is squeezed to fit.** Where the chain is too long for the place it is aimed at,
the mark is drawn in the colours of a problem rather than of a place, and letting go there
opens the form on the new times with what they run into and the offer to make room — a
question rather than a refusal, since "it does not quite fit" is usually the beginning of
a conversation and not the end of one.

The mark is a strip against the end the chain would line up with, or the whole band filled
and outlined where the whole band is the target, and it is drawn lighter than the colour it
is taken from: it is a hint about what would happen and not a line anybody has drawn yet.
What is being carried is drawn over the list, so that it is never half behind what it is
being moved about, and half transparent, because the place it would land is often directly
underneath it: the band between two events that touch is exactly as far away as the block
being carried is tall.

**The list comes to a finger held near either of its ends**, faster the nearer the edge, so
that an event can be carried to a day that was off the screen when it was picked up. What
moves is the list and not the block, which stays where the finger is — so the scroll is
added to what the finger has carried, and the aim is asked again, the bands having gone
past underneath. The line being carried is **pinned** for the length of the gesture: a list
composes what is on the screen and throws away the rest, and the drag lives on that line —
unpinned, a chain carried from one end of a fortnight to the other would be let go of at
the top of the screen and land wherever it had last been aimed.

**A schedule is read as often as it is written, and the two are different screens.**
Writing it down happens before the trip; reading what is next happens during it, and then
the empty hours, the chains and the invitation to add something are all in the way. So
once the trip has begun the tab opens **read-only**: no chains, no invitation, no stretches
at the ends of days, and no days with nothing in them. The hours *between* two things stay
— what is free is half of what anybody wants to know on the road. The menu switches either
way whenever somebody likes, and a setting says whether to start that way at all.

**Two lines of notes is the whole of the question.** Notes that fit in two are simply
there, with no control at all — a chevron that can only ever say "that was all of it" is
worse than no chevron. Longer notes run out rather than stopping: the text fades away down
those two lines, which says there is more without spending a line on saying it.

The chevron that opens the rest belongs to the **block**, not to the notes it happens to
act on, so it stands in the middle of the block and against the foot of it. Where there is
money down there it shares that line — the figures against the right, the chevron in the
middle — and where there is not it takes a line of its own, so that it never lies over the
last of the notes it has just opened.

They are on the block whether the schedule is being read or written. What an event is
about is as much the notes as the name, and a line of them read while a plan is being
written is a line nobody has to open a form for.

**The trip form refuses to be shortened over a schedule.** Dates that no longer cover what
is planned would leave events nothing can show and no form can reach — the same refusal a
dictionary row makes while something points at it. It is asked once, on Save, rather than
on every keystroke: it takes a read of the schedule.

**The schedule is the one tab with no add button.** Every other list is empty until
somebody fills it, so it needs one; this one is never empty — an unwritten plan is still
every day of the trip, each of them tappable and saying so. A button beside them would be
a second way in that answers a different question from the one being asked, since it
cannot know which hours were meant.

An event can carry **a planned expense** — a category, an amount and a currency, all three
or none — which is what the budget reads as the finer plan for that category. It does not
add to the budget: while a category is still being planned the budget's own lines are the
estimate, and once its planning is closed the schedule replaces them outright, being the
better answer by then. Ten nights at fifty dollars becomes the nights actually booked, at
what each of them actually came to.

**What it is for is the question that comes first and the only one always asked.** A sum
with nothing to file it under says nothing the budget can read, so the amount and its
currency arrive with the category and go with it. The category has the whole width and
keeps it whether it has been answered or not — a field that narrowed itself the moment it
was filled in would be answering by moving. What takes the money back off stands at the
end of the line it undoes, round and the size of a button rather than the size of a field,
because a button the height of a field standing in a row of them only looks like a field
that failed.

A category the schedule knows about and the budget never did appears in the budget all the
same, with a plan and no lines under it: there is nothing else to read it off, and a plan
nobody can see is a plan nobody can use. Closing the planning of a category with nothing
booked plans nothing — "this is the whole of it" said over an empty schedule is a figure
and not an absence.

What was settled about a category is forgotten once the category has nothing left in the
budget at all, lines or costed events. A flag on a category that is not in the budget has
nothing to be about, and would sit there invisibly until the day it surprised somebody.

**A plan is shared as a sheet of paper.** Somebody asked to look at one wants to read it,
not to open a spreadsheet, and a photograph of a fortnight is a photograph nobody can
read: an image does not break where a page should break, cannot be searched, and cannot be
quoted back. A PDF can, prints, and goes into any messenger as one file — and because it
is drawn rather than photographed, it comes out of the same page-drawing code that a
printer would be handed, which is why the system's own share sheet offers Print beside the
messengers without the app doing anything about it.

What it shows is what the tab shows while a schedule is being **read**: the days that have
anything in them, what is booked, and the free stretches between two things. Not the
chains, the ends of days or the empty days — those belong to the writing half of the
screen, and a sheet of paper is nobody's writing half.

What the screen says by stacking, the page says in columns: when it begins, when it ends,
what it is with its notes under the name, how long it lasts, and what it is expected to
cost. An end the event does not have in this day is left out, and so is the money, which
belongs to the day the event finishes in. Where the clocks move, the hour says so in
words — `12:00 TZ−10` — and not only with a raised figure: on the screen that figure is
read off a list a person is already holding, and on a page handed to somebody else it is a
number with no idea what it counts. The lengths are set over the middle of their column,
being a column of bare figures and nothing else.

The column titles stand at the top of every page — a sheet read from its second page has
nothing else to say which figure is which — and so does the date, where a day runs over a
break: a column of hours with no date over it is a column of hours. A date never ends a
page alone either, going over with the first thing under it instead.

The file is written to the cache and handed over by the same per-file link a receipt
photo is: it exists for the length of a share, and we are never told when the other app
has finished reading.

**A plan is also handed over as a spreadsheet**, which is the other thing somebody does
with one: sort it, filter it, add a column of their own. That is a different document from
the page — no days as headings, no descriptions of them, both ends of everything spelled
out — and it is set out in §6.4.

---

## 2. Rules that shape the code

These are decisions, not conventions. Breaking one silently produces wrong numbers or
wrong text rather than a compile error.

**Money is an integer count of thousandths.** `Money(milli: Long)`, scale 3, the same for
every currency. Floating point drifts once a trip's entries are summed. The 1/1000 scale
covers the three-decimal currencies (KWD, BHD, JOD).

**`Currency.decimals` is a display and input concern only.** It says how many decimals to
show — 2 for USD, 0 for JPY, 3 for KWD — and never how the amount is stored. It comes from
ICU at seeding time.

**A dictionary name has two forms.** Preloaded rows store the text in the language they
were seeded in *and* a `name_key` naming a string resource. `resolveName` prefers the
resource, so changing the device language changes the names. A row a person created has no
key and keeps its text.

This holds for favourite titles as well, and there it has a consequence: the form shows the
resolved text, so the row has to be recognised by that text on the way back. Both the entry
form's star and the category form's list keep the loaded rows and match against the
resolved name; a favourite that is edited or typed afresh loses its key and becomes plain
text, exactly as a renamed dictionary row does.

**Parsing and formatting numbers is locale-aware, and grouping is the trap.** A formatted
`50 000` fed back into a field parses as `50` in a locale where the space or comma is the
decimal separator. Editable fields use the ungrouped `formatAmountForInput`; display uses
`formatAmount`.

**An amount and its symbol are one figure, and are written as one.** `formatAmountWithCurrency`
joins them with a hard space, so that no column can put the number on one line and what it
is on the next, and wraps the pair in a left-to-right isolate, so that a symbol in Arabic
script — `ر.ع.` for the Omani rial — neither turns the pair round nor drags the comma
between two totals to the wrong side of itself. Both marks are for showing text with;
nothing written to a file goes through this function, since an exported table carries the
figure and the currency in columns of their own.

**Every amount is typed into one field, `MoneyField`.** It aligns its digits to the right
and, on losing focus, settles what was typed into the shape the currency has — 5 becomes
5.00 in dollars and stays 5 in yen. Not while typing: a separator just entered would be
taken away again. Amount fields start empty; a half left empty or at zero is read as
unused, not as an error — except on a category that takes both halves, where it is one.
An amount may be negative, though not zero: a deposit given back is a negative amount on
the row that paid it, rather than an income of its own, and the totals, the balances and
the statistics take it as such.

**A field that is written in is capitalised, a field that is searched in is not.** Names,
titles, notes and places all begin with a capital, so the keyboard offers one; a search box
does not, since a search is typed in a hurry and matched without regard to case anyway. Two
fields are neither: a currency's code is capitals throughout, and its symbol is whatever
the symbol happens to be — half of them are lower case. This is what the keyboard suggests,
not what the field enforces: a name typed in lower case stays that way.

**Photos live on the filesystem, not in the database.** A blob would ride along in every
`SELECT *`, risk the 2 MB `CursorWindow` limit, and be re-uploaded whole on every backup
because the database file changes constantly. A record stores only a file name; a
`content://` link would be wrong, since the permission behind it can be revoked.

**Receipts are excluded from cloud backup, covers are not.** Auto Backup allows 25 MB per
app and silently stops backing up anything past that, which would cost the database as
well. Covers are small and worth carrying to a new phone.

That makes the covers the only photos spending that 25 MB, so they are encoded harder than
receipts: quality 70 against 85. A receipt is kept in order to be read back and is not the
place to save bytes; a cover is a wide band behind a dimming gradient with the trip's name
over it, and never looked at closely enough for the difference to show.

---

## 2.1 The look

**The palette is written down, not taken from the wallpaper.** Material's dynamic colour
would hand every phone a different scheme, and the distances between the four levels below
are what a form is read by, so they are a decision rather than a device setting. Both
schemes live in `ui/theme/Theme.kt`; nothing in the app names a colour of its own.

**Four levels, one blue, darkest asks loudest.** Light theme in brackets:

| Level | Role | Used by |
| --- | --- | --- |
| Finishes the job | `primary` (`#1F3D70`) | Save, export, the round add button |
| Helps along the way | `primaryContainer` (`#C3D5EE`) | Take a photo, add a wallet, capture coordinates |
| Offered, not demanded | `secondaryContainer` (`#D8E4F5`) | Favourite titles, the selected tab |
| Somewhere to type | `surfaceContainer` (`#EAF1FA`) | Every field, the tab bar, menus and sheets, and the blocks a list of records is read as — an event of the schedule, a wallet |

A field label floats onto the top border once the field has something in it, and the border
leaves a gap for it. On a filled field that gap would show the page and cut a pale notch
out of the fill — the break takes the fill as well as the stroke — so the label carries the
fill with it (`FieldLabel`). The backing is drawn rather than laid out: Material sizes the
break from the label it is given, so padding the label only widens the break by as much.

**Two kinds of text, and only one of them grows.** Everything a person reads — a name, an
amount, a note, a date, the words of a form — takes the device's font size, which is what
that setting is for. The few words that *label* something instead of saying it do not: a
field's floating label, the caption under a tab's icon and the name under an icon in the
category grid keep the size they were drawn at (`UnscaledText`, which hands its content a
density with the font scale taken out). Each of them names something already in front of
the reader — the field's own contents, the icon over the word — and each is read while
somebody works out where things are rather than every time. Grown by half again they are
what breaks these layouts and nothing else is: a tab caption wraps onto a second line and
shoves the bar up into the list, a label runs out of the border it floats on, a category's
name takes three lines of a cell as wide as the icon above it. Nothing else in the app is
held back this way — the icons themselves grow, being what a person is looking at.

The disc under an icon on a list row is the exception to the ramp: it is a shade off the
card rather than a tint, since the card is neutral and a blue coin on grey has nothing to
rank itself against.

Corners rank a control the same way: only a button is a lozenge. A field and a favourite
are both 12dp, which on a 32dp chip reads rounder than on a 56dp field — and a chip at 16dp
would *be* a lozenge, leaving half a tone to tell a favourite from a button.
An aside inside a card — recount, reconcile — stays a text button, a step below the levels
above: the card itself is already tappable, and a fourth filled shape on it would take that
away.

A destructive action inside a form — deleting a photo, dropping every photo of a trip — is
a secondary button in the error colours: the weight of the actions beside it, and a colour
that says what it does. Never the filled button, and never bare red text, which among
lozenges reads as a link. Deleting the record itself is the icon in the top bar of its form.

**Cards are neutral, everything else is blue.** `surfaceContainerHighest` is out of the blue
ramp on purpose. Trip and wallet cards carry photographs, and a tinted card seen through a
faded picture turns the picture that colour.

**Dark keeps the blue rather than inverting it.** Material's dark palettes make the filled
button pale with dark text; here it stays a lighter blue with white text (`#3A62A8`). A
white bar across the foot of every form glares, and the colour is the thing that says which
button finishes the job. The four levels keep their order, counted from the background up.

**The keyboard is the screen's own problem.** The window is drawn edge to edge, so the
system no longer shrinks it when the keyboard comes up: every screen with a field in it
pads itself by `keyboardInsets()`, which is the whole of the keyboard's inset, navigation
bar and all — the keyboard covers that bar, and keeping room for it would leave a band of
nothing at the keyboard's foot. Nothing underneath pads for the navigation bar twice
either, since padding by an inset consumes it: the bar the save button sits in asks for the
navigation bar while the keyboard is up and is told there is none left, and gets the whole
of it again once the keyboard is down.

**One file per level, not per screen.** `ui/common/Buttons.kt` holds `PrimaryButton`,
`AddFab`, `SecondaryButton`, `FavoriteChip` and `RowIconButton`; `formFieldColors()` and
`FieldShape` do the same for fields. A screen picks a level; it never picks a colour.

**The type scale is Material's, and a style is never built from scratch.** Material builds
every style it ships on a base that sets `includeFontPadding = false` and centres the line
box on the text; a `TextStyle(...)` written by hand leaves both unset and quietly returns to
the legacy metrics, even when its sizes match to the sp. That is what the template's
`bodyLarge` override did — and `MaterialTheme` hands `bodyLarge` to the whole app as the
default text style, so it reached every `Text` that names no style of its own. To override
a style, copy it from `MaterialTheme.typography` and change what needs changing.

---

## 3. Architecture

### 3.1 Layers

```
Compose UI  ──uses──▶  ViewModel  ──uses──▶  Repository  ──uses──▶  DAO / Room
                           │                     │
                           └──────uses───────────┴────────▶  core/ (money, text,
                                                       photo, location, export, stats)
```

One direction only. Composables never touch a DAO; repositories never touch Compose; the
`core` package knows nothing about either and holds no Android UI types.

State goes up as a `StateFlow<…State>` of immutable data classes. Room DAOs return `Flow`,
repositories pass them through, and a ViewModel `combine`s them with its own form state
into one screen state. The one query read by several screens at once — a trip's entries,
under all three of its tabs — is shared by the repository, so that Room runs it once per
change rather than once per tab. One-off things a screen has to say — close, or a message for the
snackbar — travel as channel events, not as state fields, because they must happen once
rather than again on every recomposition.

### 3.2 Package map

| Package | What lives there |
| --- | --- |
| `core/money` | `Money`, `Quantity`, `MoneyFormat`. Pure Kotlin, no Android. |
| `core/balance` | `WalletBalances` — what a wallet holds, worked out from its entries; the rules of 1.4 and 1.5. |
| `core/text` | Resolving a dictionary name against its string resource. |
| `core/photo` | `PhotoDirectory` and its two folders, image decoding and scaling, sharing. |
| `core/location` | Reading a fix, formatting coordinates, building a `geo:` link. |
| `core/stats` | `TripStatistics` — what a trip came to, by currency and category; `BaseTotals` — the same brought to one currency through the trip's rates; `expenseChartCategories` — whether a circle can be drawn of it, and of what. |
| `core/checklist` | Gathering a trip's checklist items into their sections, and working out what a copy from another trip would add. |
| `core/rates` | `RateLookup` — one exchange rate off the internet, the only place the app opens a socket; `Quote`, `quotesTo` and `convertBetween` — the trip's rates as a lookup, and one currency read into another through the base; shared by everything that converts. |
| `core/budget` | `plannedByCurrency` and `spentByCurrency` — what a category comes to on either side, in the currencies it was written in; `BudgetVersusActual` — the two read against each other in the base currency; `onlyNew` — what a table read in adds. |
| `core/schedule` | The timeline, where nothing knows about Android or Room but the entity. `Zoning` and `place` — which clock is in force when, and each event read on it; `days` — the trip cut into days that are not twenty-four hours long; `bands` — a day as an unbroken column of events, gaps and clashes; `clashes` and `settle` — what is booked twice and what would have to move; `reanchor` — giving every event back the hour it was typed at once the zones under it have moved; `newEventIn` — what tapping empty time offers to put in it; `landingFor` — where letting go of a dragged chain would put it, and whether there is room. |
| `core/export` | `TripTable` — the columns; `TripCsv` renders them and `TripXlsx` names them for `Xlsx`, which writes any table as a sheet; `CsvReader` and `TripCsvImport` read one back; `CsvWriter` — the quoting both tables share; `ChecklistCsv` and `ChecklistCsvImport` — the same for a checklist, `BudgetCsv` and `BudgetCsvImport` for a budget, `ScheduleCsv` and `ScheduleCsvImport` for a schedule; `PlanTable` — the schedule's own columns as a sheet; `PlanLines` cuts a schedule into the lines a page is made of and `SchedulePdf` draws them onto one; `ReceiptsZip` gathers the photos. |
| `core/dump` | `Dump` — the whole database as one JSON document; `DumpArchive` — that document and the photos as one ZIP. |
| `data/entity` | Room entities and the `@Relation` views over them. |
| `data/dao` | Queries. |
| `data/db` | `AppDatabase`, type converters, `DefaultData` seeding. |
| `data/prefs` | DataStore: the last screen and the tab last worked on, the base currency, the home time zone, the hours a schedule assumes and how it opens, the photo settings, the columns the last spreadsheet was written with, whether the dumps are on the menu. |
| `repository` | A thin layer over the DAOs; the only thing ViewModels are given. `DumpRepository` is the one that does more: it takes the whole database out and in. |
| `ui/common` | Scaffolds, form fields, buttons, pickers, the overflow menu, dialogs; `WalletForm`, the fields and the check of the wallet sheet, which three screens open; `DictionaryEditViewModel`, the frame the three dictionary forms share. |
| `ui/theme` | The two colour schemes, the corner scale, the type. |
| `ui/trip`, `ui/entry`, `ui/budget`, `ui/schedule`, `ui/checklist`, `ui/statistics`, `ui/reconcile`, `ui/settings` | A screen and its ViewModel per folder. |
| `ui/navigation` | `Route` — the destinations. |
| `di` | Hilt modules. |

### 3.3 Navigation

`Route` is a sealed interface of `@Serializable` destinations, so arguments travel as
typed fields and a call with the wrong parameters fails to compile. The graph is declared
in `MainActivity`; screens receive plain lambdas and know nothing about the controller.

The app remembers the trip it was last in and the tab last worked on (DataStore) and
reopens both, but always builds the trip list underneath, so Back from the trip returns to
the list instead of closing the app. Deleting a trip from its form goes back there too,
past the screen of the trip that is no longer there.

### 3.4 Dependency injection

Hilt. The database is provided as a singleton, DAOs from it, repositories by constructor
injection, ViewModels with `@HiltViewModel`. There is deliberately no static database
factory: nothing can open a second connection behind the app's back.

---

## 4. Data model

Fifteen entities, database version 2. **The columns below are examples chosen to show what an
entity is for — not the full list.** Read `data/entity/` for that.

| Entity | Table | Holds | Example columns |
| --- | --- | --- | --- |
| `Currency` | `currencies` | The currency dictionary. | `code` (PK), `symbol`, `decimals`, `name_key`, `used_at?` |
| `PaymentMethod` | `payment_methods` | Cash, cards, transfers. | `name`, `icon`, `fixed_currency` |
| `Category` | `categories` | What an entry is about. | `icon`, `is_expense`, `is_income`, `track_quantity` |
| `CategoryFavorite` | `category_favorites` | Titles offered for a category. | PK (`category_id`, `name`), `name_key?`, `sort_order` |
| `Trip` | `trips` | A trip. | `start_date`, `end_date`, `photo_file?` |
| `TripWallet` | `trip_wallets` | A payment method in one currency on one trip. | `trip_id`, `payment_method_id`, `currency_code`, `name?`, `opening_balance_milli`, `last_used_at?` |
| `TripRate` | `trip_rates` | What one currency of a trip is worth in another. | `trip_id`, `from_currency_code`, `to_currency_code`, `rate_milli`, `multiplier`, `note`, `note_from_lookup`; unique on the first three |
| `ChecklistItem` | `checklist_items` | One thing to do before the trip. | `trip_id`, `section`, `title`, `is_done`, `is_skipped` |
| `BudgetItem` | `budget_items` | One line of what the trip is expected to cost. | `trip_id`, `category_id`, `title`, `notes`, `quantity_milli?`, `amount_milli`, `currency_code` |
| `TripBudgetCategory` | `trip_budget_categories` | What has been settled about one category of a budget. | `trip_id`, `category_id`, `is_spending_closed`, `is_planning_closed`; unique on the first two |
| `ScheduleEvent` | `schedule_events` | One thing that happens at a known time. | `trip_id`, `starts_at`, `duration_minutes`, `starts_in_zone?`, `ends_in_zone?`, `follows_id?`, and the planned expense — `category_id?`, `amount_milli?`, `currency_code?` |
| `ScheduleDayNote` | `schedule_days` | What one day of a trip is about, in a line. Belongs to the date and to no event on it; unique per trip and date, and absent where nothing has been said. | `trip_id`, `date`, `summary` |
| `Entry` | `entries` | The record the app exists for. | see below |
| `BalanceSnapshot` | `balance_snapshots` | A wallet recount and when it happened. | `wallet_id`, `amount_milli`, `date_time`, `notes` |
| `IconPreset` | `icon_presets` | Emoji offered by the icon picker. | PK (`target`, `emoji`) |

`Entry` is the wide one. Its columns fall into groups:

- identity and context — `trip_id`, `category_id`, `date_time`;
- description — `title`, `place`, `notes`, `quantity_milli?`;
- the expense half — `expense_wallet_id?`, `expense_amount_milli?`,
  `expense_currency_code?`;
- the income half — the same three under `income_…`;
- what the wallet was actually charged, filled in later — `*_settlement_amount_milli?`.
  No currency beside it: it is the wallet's, and a wallet keeps the one it was made with;
- attachments — `photo_file?`, `latitude?`, `longitude?`.

`EntryDetails` is the read view: the entry plus its category, both wallets and the two
currencies it was written in, pulled in through `@Relation`. The wallets are nested
relations — each brings its own payment method and currency — and two relations to the
same table on different columns is the shape to keep in mind when adding another.

### 4.1 References

```
Currency ◀── TripWallet.currency_code                  (restrict)
         ◀── Entry.{expense,income}_currency_code      (restrict)
         ◀── TripRate.{from,to}_currency_code          (restrict)

Trip ◀── ScheduleEvent.trip_id                         (cascade)
     ◀── TripWallet.trip_id                            (cascade)
     ◀── ChecklistItem.trip_id                         (cascade)
     ◀── TripRate.trip_id                              (cascade)
     ◀── Entry.trip_id                                 (cascade)

TripWallet ◀── Entry.{expense,income}_wallet_id        (restrict)
           ◀── BalanceSnapshot.wallet_id               (cascade)

Category ◀── Entry.category_id                         (restrict)
         ◀── CategoryFavorite.category_id              (cascade)
         ◀── ScheduleEvent.category_id                 (restrict)

PaymentMethod ◀── TripWallet.payment_method_id         (restrict)

ScheduleEvent ◀── ScheduleEvent.follows_id             (set null)
```

A trip deletes its contents — wallets, rates, checklist, budget, schedule and recounts — and
a wallet deletes its own recounts.

The one reference that neither cascades nor refuses is an event's link to the one before
it: the link is worth less than either event, so deleting a flight unchains what followed
it and leaves that where it stood. A dictionary row refuses to go while anything points at it — that refusal is
what the UI reports as "this currency is used by wallets or entries". A wallet refuses the
same way, but only for entries: a recount is not a record of its own.

A rate is the one row nothing points at, so it is also the one that can always be deleted.
It is a lens on the entries rather than part of them: taking it away changes how the totals
are read and nothing else.

### 4.2 Seeding

`DefaultData` fills the dictionaries by writing `ContentValues` straight into
`SupportSQLiteDatabase` from the creation callback: synchronously, before any query can
return. Going through the DAOs would mean a background coroutine, and the first screen
would meanwhile show empty dictionaries.

It runs from `onCreate`, when the database is first made, and again from `onOpen` whenever
the tables turn out to be empty — which is what happens after a debug build has dropped
and recreated the database on a schema change. Room has a callback for that very moment,
`onDestructiveMigration`, but seeding cannot go there: Room calls it after the old tables
are dropped and before the new ones exist, so an insert made from it has nowhere to land.
Checking on open is the way round that.

### 4.3 State that is not in the database

| Where | What |
| --- | --- |
| `filesDir/receipts/` | Receipt photos. Out of cloud backup. |
| `filesDir/trip_covers/` | Trip covers. Backed up. |
| DataStore `app_state` | Last screen and the tab last worked on, the base currency, the home time zone, the hours a schedule assumes and how it opens, photo settings, the columns the last spreadsheet carried, whether the dumps are on the menu. |

Both photo folders are swept at start-up: files no record points at are deleted, which is
what catches a form abandoned after a capture.

---

## 5. Screens

**An empty list explains itself.** Every tab of a trip says, while it has nothing in it,
what belongs there in a short paragraph, then that there is nothing yet, then which button
changes that. The paragraph is the one place a screen can say what it is for: once anything
is in the list it is gone for good, and it is written for somebody standing in front of the
tab for the first time. Where the same thing has to be explained on a form as well — a
wallet, an exchange rate — it goes behind an **i** beside the heading instead, since there
the list is not empty and the paragraph would be in the way.

**A record of a trip is edited on a screen, not in a sheet.** Every tab — the budget, the
plan, the checklist, the wallets, the entries — opens the form of its record over the whole
screen, with Back and Delete in the top bar and Save across the foot, because that form is
the whole of what a person came to do. The forms are drawn from the trip's screen rather
than from inside the tab that opens them: a tab lives under the top bar and the tabs, and
cannot put anything over them. A sheet is for a question asked while a form is already open
and not yet finished with — a wallet made from inside an entry, or on the trip form, where
the wallet is one detail of the trip being set up. Where the same fields are wanted in both
shapes, they are written once and given the frame that suits, as the wallet's are.

| Screen | Does |
| --- | --- |
| Trip list | Cards with cover, dates and an edit button; the menu; add a trip. |
| Trip form | Name, dates, cover, exchange rates, wallets with names and opening balances, delete. Saving a new trip opens it. Rates stand above the wallets and work the same way — a row apiece, a sheet to edit one in, all of it written when the trip is saved — but read as equations, "1 USD = 84.78 RUB", and a rate can always be removed since nothing points at one. A rate carrying a note is two lines, the note under the equation in the same quiet type a wallet's opening balance is set in, and padded as a wallet row is; one with nothing to say is a single line left to the height of its own delete button, since the same padding there would leave a gap the height of a second line that is never coming. Their sheet is laid out like the amount row of an entry, a figure on the left and its currency on the right, twice over: the units above, the rate below against the base currency, which is shown and not chosen; then, under the button that fetches a rate, the note that button fills in. Each of the two headings carries an **i** opening the paragraph that says what the thing is for — what a rate here is allowed to be, what a wallet is and why there may be two of the same kind. |
| Trip | Five tabs over one top bar, in the order a trip is lived through — the budget is written, then the plan, then the bag is packed by the checklist, then the money is spent: **Budget**, **Plan**, **Checklist**, **Wallets**, **Expenses**, reached by tapping the bar at the foot or by swiping sideways. That bar is raised over whatever is being read and casts its shadow onto it: it and the blocks on every tab are the same shade of surface, so a list ran straight into the tabs, and a rule loud enough to stop it was louder than anything it was separating. The shadow says the same thing without drawing anything — the tabs are not part of the list, they are over it. The top bar carries back, the trip name, an edit button and the export menu; the tab decides what the add button makes. **Which tab opens** is the one last worked on, whichever trip that was: carrying on where a person left off is what they expect, and it holds across trips — going out to the list and into another trip lands on the same tab, since somebody keeping an eye on the plan is keeping an eye on the plan of whatever they open next. Until there is a tab to carry on from — a first run — a trip opens on its entries, or on the wallets while there is none to write an entry against. The tab is written down as a person moves between tabs rather than as they leave, there being no moment the app is told it is closing, and it is stored by name, as the spreadsheet's columns are, so that a tab added or moved cannot turn a saved answer into a different one; a name no tab answers to reads as nothing remembered. |
| — Budget | A band per category with its lines under it, laid out as the statistics are: what the line is called on the left, what it comes to on the right, and under the name the two figures it was arrived at — so many at so much. A line with no name of its own is known by those figures instead. The row opens the form on a tap and carries nothing else: changing the words and taking the line away both happen there, which leaves the list to be read. Deleting does not ask, being one line that was one line to type. The band carries a three-dot menu of its own with the two things a category has to say about itself — **Planning closed** and **Spending closed**, separate questions either of which can be answered without the other — and under the category's lines its own totals: budget, booked and spent, per-currency amounts joined by commas, with a line for each thing declared above them. The whole run of categories stands under a heading of its own, a shade deeper than the bands; then the base currency section, headed the same way, three columns wide and totalled, laid out as the statistics are — columns while the figures fit across the screen and, where they do not, a list of labelled lines under each name, exactly as there — or, in place of it, the one sentence saying which rate or which setting is missing, with the link the statistics give. The form takes the whole screen, with Delete in the top bar and Save across the foot as every other form has them. The add button opens it on the category picker, as a new entry does — nothing else can be filled in before the line is known to be for, and the category brings its favourite titles with it. Then the name, with its star and its notes folded behind the same chevron the entry form uses and the favourites as chips under it; then the price with the quantity beside it, and the total with its currency, the two narrow fields of equal width so the figures line up. A new line starts at a quantity of one, in the currency the last one was written in. Typing into any of the three figures moves the one that follows from it. The tab's own menu carries closing the spending, the currency converter, and taking the table out to a CSV and back. |
| — Plan | A heading per day of the trip, empty ones included, and under each an unbroken column of what is booked, what is free, where two things touch and where they claim the same minutes. The heading is the deeper of the two bands a trip is read by, the blocks under it the lighter one, and what separates what is booked from what is free is the shadow they stand off the page by. Beside its date a heading carries a line saying what the day is about, set more quietly, growing the band downwards where it runs past one row with the date staying at the top; tapping the heading writes it while the schedule is being written, and emptying the field takes it back. An event is two lines tall at the least and built downwards in the order it is read: the name, then its notes, then what it is expected to cost with its category, in the corner. When it begins and when it ends stand down the left against the whole of it, each against its own small mark; how long it lasts is at the top right, written as a padded clock, and is the only thing beside the name, so the name has the width of the block up to it. The notes are set in to the name and run the full width. Two of their lines are shown: notes that fit in two are simply there, and longer ones fade away down the second. The chevron that opens the rest stands in the middle of the block against the foot of it, sharing that line with the money where there is any and taking a line of its own where there is not. The figures are quiet and the marks quieter, the name being what is looked at. A long name grows the block from the middle, the clocks staying against their own edges. Where the clocks moved, the hour carries how far by as a small raised figure, the way a ticket writes the day it lands on. One carried across midnight appears under both headings, cut square along the join and printing only the end it has in that day, with the whole length given in both and the end it does not have in that day left out altogether, mark and all. What it is expected to cost goes on the piece that ends it and nowhere else: one event costs what it costs once, and the figure under every heading it crossed would read as that much a night. A gap is one line, "Tap to add" on the left and, between two events, its length on the right level with the lengths above and below. An event is moved by holding it and putting it down: against the top of a gap it begins where the thing above it ended, against the bottom it ends where the thing below it begins, an empty day takes it at the hour it already had, and the band over minutes claimed twice settles which of the two gives way. The whole chain goes, the mark is a light strip or a filled and outlined band, and where the chain does not fit it is drawn in the error colours and letting go opens the form on those times. The list comes to a finger held near either of its ends, and the line being carried is pinned so that the drag survives the list running out from under it. Two events that touch carry a chain lying across the join instead, half over each block, pale where they merely touch and solid where one follows the other; a join on midnight is drawn above and below the day heading, and either tap settles the one link. Minutes booked twice are a band in the error colours, square against both events that claim them and raised with them, and it opens nothing. Every block is set off from the next by a hair of space so that none of them runs into another. Nothing is to scale: this is a list of what happens next, not a chart of how long it takes. The tab opens on the day the trip is on, and has no add button: an unwritten plan is still every day of the trip, each of them tappable. Once the trip has begun it opens read-only — no chains, no invitation, no ends of days and no empty ones, but the hours between two things stay. The menu switches either way, hands the plan to another app as a sheet of paper, and carries it out to a CSV and back. The form takes the whole screen, with Delete and Repeat in the top bar and Save across the foot as every other form has them; Repeat makes a second event like this one, free of any link and on the same hours, which is a clash and says so. It is read downwards in the order the questions come. First the name, with its notes folded behind the same chevron the entry form uses. Then what it begins at the end of, which is not a list but the one record near enough to have been meant — somebody writing "collect the bags" at twenty past five means the flight that landed at ten past — named, with the hours it takes up written as the list writes them, one over the other against the same two marks and without the zone; at the end of that line the chain the list draws over a join, as a round button, filled while the two are tied and not while they are free. The question is left out altogether where there is no such thing. Then the start on one line — date, hour, and the clock that hour is read on — and the end on another built the same way; while it is tied the start line is greyed, being that other event's end. The zone reads "unchanged" until the event declares one, which is what almost every event says and what the picker offers first. Then how long it lasts, written as a clock is and standing under the two clocks it is measured between, in a field of their width. The three figures move each other about — the start carries the end along and leaves the length, the end and the length each settle the other. Where these times run into something, what they run into and by how much is said right under them rather than at the foot of a long form, in a block of the colours the schedule gives minutes claimed twice, with the offer to move the others along where that would clear it — which is only where what comes after can be pushed, since a clash with something earlier would shove the morning into yesterday. Nothing moves until Save, so shutting the form takes the shift with it. Last, what the event is expected to cost: the category, always asked and always the whole width, and with it, on the line below, the amount and its currency, with a round button at the end of that line that takes the money back off. The two sections that need explaining — what tying to the nearest record does, and what a planned expense is for — are headed, with the paragraph behind an **i** beside the heading. |
| — Wallets | One wallet to a block: what it holds now — or a warning in place of it while something is waiting to be reconciled — what its last recount found, and buttons to recount and reconcile. The blocks are the ones the schedule draws its events with — the lighter shade, the same corners, raised off the page by the same hair of shadow — because they are the same kind of thing to read: one record apiece, in a list of them, on a tab of a trip. Tapping a block opens the wallet's form, which takes the whole screen with Delete in the top bar, as the forms of a budget line, an event and a checklist item do; the block itself carries no delete of its own to hit by accident. It is the same four fields the trip form asks in a sheet, written once and shown in either shape. Reconciling only appears where the payment method does not fix the currency. A wallet an entry points at refuses to be deleted and the form says why, staying open. |
| — Expenses | Labelled for what fills it rather than for what it holds: an income and an exchange are entries too, but what a trip costs outweighs what comes back, and a word read at a glance beats a more accurate one nobody scans for. Totals per currency, then the entries day by day. A day's heading shares its line with what the day cost, so the date is abbreviated rather than spelt out — weekday, day, short month, year — laid out in whatever order the locale puts those in. A total names its currency in full — name and code, in the muted colour a label gets — and sets the figures against the right edge, written as an entry writes them: what went out plainly, what came in under it with a plus, in the colour income is given everywhere. They carry the symbol, which is what decodes the symbol printed against every entry below. A name long enough to leave them no room pushes them onto the next line rather than being squeezed into a column of syllables. Beside the add button is a **search**, a round button that grows into a field of the same height and lozenge and takes the cursor with it; it keeps the entries whose category, title, notes or place carry what was typed, and the totals go while it is open — a tally of the matches would read as the trip's own — leaving the days as headings. The category is matched as it is read rather than as it was seeded, so a search in the device's language finds a row written in another. The only tab whose menu offers what the trip came to, the table exports and the import: on the others they would answer a question nobody is asking. Statistics stands at the head of that menu, above a divider, being the one of them that is read rather than filed. Those exports write the whole trip, not what a search has left on screen. |
| — Checklist | Sections as bands across the list, their items under them. A heading folds and unfolds on a tap; a row's tick settles its item and the rest of the row opens its form, unless the item is ruled out for this trip, in which case it is greyed and the tick is out of reach. Done is struck through, and so is a heading whose items are all settled. Settled sections open folded away. The form takes the whole screen, with Delete in the top bar and Save across the foot as every other form has them; a row carries nothing of its own besides its tick. A heading carries a three-dot menu with Tick everything, Untick everything, Edit and Delete. A heading is folded by an arrow drawn the way a tree draws one: along the row while it is shut, down at what it holds once it is open. Deleting a section asks first and says how many items go with it; deleting an item does not ask, being one line that was one line to type. The add button opens the item form, where the section is picked from a list with a last line for a new one. Its menu carries three items of its own: copying a list off another trip, and taking the table out to a CSV and back. |
| Statistics | Off the menu of the entries rather than a tab: a tab is somewhere a person works, and this is something they go and look at once the working is done. Three named sections. First the breakdown: each currency on its own, under its name and code — categories with their quantity, income and expense, then one netted total under whichever side the currency came out on. Then, where there is one, the trip as a single figure, everything converted to the base currency, or the line that says which currencies have no rate and links to the trip form. Last, where the figures are comparable at all, a pie of the expense categories. Each section is headed a shade deeper than the currency headings inside it. The columns are shares of the width, which a larger system font does not widen, so the figures are what give: a cell a point or two over is set a point or two smaller rather than broken across two lines, since a sum split in half has stopped being a number. Where the figures would have to give more than that — which is where anybody who has turned the font up ends up, the figures growing while their columns do not — the block stops being a table instead: each row becomes its name with its figures listed under it, every one of them labelled and at full size. It is asked of each block on its own and of the whole of that block, so a trip whose dirhams fit and whose roubles do not reads as a table in one currency and a list in the next, and never as half of each. The quantity heading is abbreviated to a word that fits the narrowest column even at the largest font. |
| Entry form | Repeat and Delete in the top bar of an entry that exists. Date and time on one row, category under them, then each half as its wallet with the amount and currency code under it — where the money moved in a currency other than the wallet's own, a settled-amount row built to the same shape, its currency fixed to the wallet's and named for the direction: charged to the account, or credited to it — then quantity and title. Two things on this form are folded away behind a chevron standing on the lower border of the field above them, and open flush against it holding the cursor: the notes, two lines tall, and that settled amount, which most halves never have and which is usually learnt later from the bank. Either opens of its own accord on an entry that already carries one. The favourites, photo, place and coordinates follow. |
| Reconciliation | One wallet's settled amounts, entered in bulk. Reached from the wallet itself. The fields form a column, headed once by what they are — the movement of the account, in its own currency — so each field needs only a word for the direction the money went. No symbol inside them: it would repeat the heading, and a symbol taller than a digit makes a filled field taller than an empty one. |
| Dictionary list / form | One pair per dictionary, on a shared scaffold. |
| Settings, About | The base currency; then the schedule — the home time zone, the hour a day is taken to begin at, how long an event is offered as, and whether a plan goes into reading mode once its trip has begun; then the photos — whether they are shrunk at all, the longest side to shrink to and the quality to encode at, the last two greyed while nothing is being shrunk — and the dumps switch, each group under a heading of its own. The labels say what these are and no paragraph repeats them. Changing the home zone asks first wherever there is a schedule to rewrite, since it rewrites every trip and shows nothing for it. the base currency is what the trip forms need before they will offer rates at all; the icon, what the app is for as a list of the things it does, its version, its author, and where its code and its issue tracker live. The base currency is the one picker in the app that offers a way back to no currency at all — everywhere else an amount without one means nothing, here having none is the starting answer. |

Every screen's overflow menu carries the same tail — the three dictionaries, then
Settings, then About — built once in `ui/common/OverflowMenu.kt` so that two screens
cannot drift apart. A trip prepends what the tab it is standing on can be asked — what the trip came to, its exports and its import on the entries; copying a checklist on the checklist — and nothing else: anywhere
there is to go is a tab or a button.

---

## 6. Export formats

Every export goes through `ActivityResultContracts.CreateDocument`, so the person chooses
where the file lands and the app needs no storage permission of its own. The suggested
names are `<trip>.csv`, `<trip>.xlsx`, `<trip>_plan.xlsx` and `<trip>_receipts.zip`, with
anything a file system might object to replaced in the trip's name.

### 6.1 The columns

The table's shape lives in `TripTable`: a `TripColumn` enum for the order and a `Cell`
for each value, typed as text, number or date. Both table exports render the same rows, so
a new column reaches CSV and XLSX at once and in the same place. The titles are supplied
from outside by an exhaustive `when` over the enum, which is what makes the compiler
refuse a column nobody has named.

Everything an entry holds is written out except its receipt photo, which the archive is
for: the date and the time, the category, the title, the quantity, both halves with their
amounts, currencies and wallets, both settled amounts as filled in during reconciliation,
then the place, the coordinates and the notes.

`TripColumn.csv` is the subset a CSV carries — everything but the two payment-method
columns, which a spreadsheet may still show. An import indexes fields by position in that
list rather than by the enum ordinal, so the two orders cannot drift apart. Coordinates go as one text field with a dot, since a pair
is read and pasted whole and a comma standing in for the decimal point would make it
useless in a map.

### 6.2 CSV

RFC 4180 in shape: CRLF between records, a field quoted only when it carries the
separator, a quotation mark or a line break, and a quotation mark inside one doubled. The
file begins with a UTF-8 BOM, without which a spreadsheet reads it as the system codepage
and Cyrillic names come out as rubbish. Dates are ISO, times are hours and minutes. Records
go oldest first.

The column separator comes from the `csv_separator` string resource — a comma by default,
a semicolon in Russian, because that is what a spreadsheet of that locale splits on. **The
decimal separator is derived from it in code, not configured beside it**: a locale uses a
semicolon between columns precisely because the comma is already its decimal point, and a
mismatched pair delivers every amount as text instead of a number. For the same reason
quoting is done against the separator actually in use and nothing else.

**A schedule's times go out as wall clocks**, which is what a person reads and what a
spreadsheet can hold, with only the zones an event actually declares beside them. The
moments are worked out on the way back in, by walking the rows in order and carrying the
zone forward exactly as the app lays a schedule out — so a file says the same hours
wherever it is read, which is the whole of what anybody wrote down. A link to the row
above travels as a mark in a column of its own, and is tied only where both ends of it
arrived: a row whose predecessor was already there has nothing new to be tied to.

### 6.3 XLSX

The same table as a spreadsheet, written by hand in `Xlsx`. An `.xlsx` is a ZIP of six
small XML parts, and the platform carries both halves — `ZipOutputStream` and string
building. The libraries that would do this instead (Apache POI and the lighter writers)
all want StAX, which Android has not got, and POI besides would add megabytes to write a
table of seventeen columns.

What it buys over the CSV beside it: a reader does not have to guess. There is no
separator to get wrong, an amount arrives as a number that sums and a date as a date that
sorts. Strings go inline rather than through a shared-strings part, which costs a little
size and saves a whole moving piece. `styles.xml` carries the least a reader will accept
plus the number formats the sheet uses — date, time, integer, two decimals, three, and
`[h]:mm` for a length. **A length is not a time of day**, however alike the two look on
the screen: `Cell.Length` goes out as its own fraction of a day under the bracketed
format, which is the only one that shows thirty hours as thirty rather than as six.

`Xlsx.write` knows nothing of any particular table — it takes a sheet name, the titles,
the widths and the rows. `TripXlsx` is what names the entries' columns and hands them
over, so a title cannot end up over the wrong column, and the schedule does the same
through `PlanTable` without the two tables having to agree about anything.

Which columns go in is asked before the file is placed, and the answer is kept in DataStore
as a set of column names — names rather than positions, so that adding or moving a
column cannot silently turn a saved choice into a different one. An empty choice cannot be
confirmed, so an empty stored set unambiguously means nobody has chosen yet, which the
dialog reads as all of them. The two tables keep their answers apart: a column ticked for
the entries says nothing about the plan. `ColumnPickerDialog` is generic over whatever is
being ticked and asks the caller for the name of one.

### 6.4 The plan as a spreadsheet

The schedule has a sheet of its own, and it is not the schedule's CSV. The CSV is read
back by this app and must say what an event *is* — hence a link to the row above and a
length instead of an end, the end being worked out from the two. A spreadsheet is read by
a person, who wants both ends spelled out and has no use for a link they cannot act on.

So `PlanColumn` carries both ends as a date, a time and a zone apiece, then the name, the
notes, the length, and the planned expense as a category, an amount and a currency code.
The headings over the first six are short — "Starts", "Time", "TZ" — because a heading read
sideways is nobody's spreadsheet and which end a clock belongs to is already said by the
date beside it; the dialog that asks for them spells them out instead, having nothing
beside them there to say which end is meant. The zone column carries the offset alone and
only where the clocks actually moved: a column reading `+3` on every line of a trip that
never left home is noise.

**Free time and minutes booked twice are rows in their own right**, which is the other
place it parts from the CSV: a file that is read back would turn every gap into an event,
while a sheet is looked at, and what is *not* booked is half of what anybody looks at a
schedule for. They carry their two ends and their length and nothing else — a gap has no
name, a clash is named by the one word, and neither states a zone, there being nothing
there to state one. The ends of a day are still left out: that is the night.

The dates are neither grouped nor headed, and the day descriptions do not travel. A
spreadsheet groups and sorts for itself, and a heading row in the middle of a table is a
row that breaks both.

And because nothing is grouped by date, **an event is one row however many dates it
covers**. The days cut a night flight into a piece under each heading — which is what a
column of days has to do, and what the page repeats its heading for — but an ungrouped
table has no heading to repeat, so a second piece would read as a second flight. Only the
first is written, carrying the event's own two ends and its whole length.

### 6.5 Reading a CSV back

`CsvReader` is a proper RFC 4180 parser rather than a split on the separator, so a title
with a comma or a note with a line break comes back as it went out. `TripCsvImport` turns
records into entries and refuses everything else.

Three decisions shape it:

- **The first line says what is where.** Columns are found by their titles, in whatever
  order they come, so a file cut down or rearranged in a spreadsheet still reads: the
  date, the category and the three expense columns have to be there, the rest
  may be left out, and a title the app does not know is passed over. The separator is
  whichever one makes the first line name the most columns, since the file may have been
  written on a phone whose locale splits columns differently. The titles are localised,
  so a file reads back on a phone in the language it was written in; one written in
  Russian is refused on an English one rather than read wrongly.
- **The category says which halves a row must carry**, exactly as it does on the form: an
  expense, an income, or both for an exchange. A half the category calls for that is
  blank, or whose column the file has not got, stops the import at that row; so does an
  amount on a half the category has not got, since that is money the entry could not show.
- **All or nothing.** A row that cannot be understood stops the import and is reported
  with its line number, the column, and the value at fault. Half an import would leave no
  way of telling by eye which half arrived.
- **Nothing is invented.** A category the dictionaries do not know, or a wallet the trip
  does not have, is an error rather than a reason to create one. Both are matched by the
  name the export actually put in the file, resolved the same way.
- **A half is held to the wallet's rules**, as on the form: a wallet of a method that
  fixes its currency takes that currency only, a settled amount is taken only where the
  currency paid in differs from the wallet's, and an amount is rounded to the decimals
  its currency has, so that a file cannot put into the database what the form would not.

Both the day and the time of day survive the round trip; a row whose time is blank, or a
file without the column, starts its day. A date is read in the ISO form the export
writes and in the day-first form a Russian spreadsheet saves it in, 01.05.2025.

### 6.6 The receipt archive

One ZIP holding the photos of every entry that carries one, oldest first. On disk a photo
is a UUID, which says nothing to a person once the file is out of the app, so inside the
archive it is renamed after the entry it belongs to:

```
001 2026-09-09 Lunch at the pier.jpg
002 2026-09-09 Groceries.jpg
```

The number keeps the order in any file manager and makes the names unique; the label is
the entry's title, or its category when the title is blank. JPEGs go in deflated at level
zero, which is stored in all but name: deflating them again costs time and saves nothing
measurable. An entry whose
file has gone missing is skipped rather than failing the export, and the count reported
afterwards is what actually went in.

---

## 7. Testing

| Kind | Where | For |
| --- | --- | --- |
| JVM unit | `app/src/test` | Pure logic: money arithmetic and parsing, the CSV format, form-state rules. |
| Instrumented | `app/src/androidTest` | Room queries and relations, seeding, photo storage, location, start-up. |

Anything that needs `android.*` beyond stubs goes to `androidTest` — `Uri`, `Bitmap` and
`ExifInterface` are not usable in a JVM test.

---

## 8. Build

Kotlin 2.3.10, AGP 9, KSP for Room and Hilt. minSdk 26, targetSdk 36.

| Dependency | Version | For |
| --- | --- | --- |
| Compose BOM | 2026.02.00 | UI, Material 3 |
| Room | 2.8.4 | Storage |
| Hilt | 2.59.1 | Injection |
| Navigation Compose | 2.9.7 | Type-safe routes |
| DataStore | 1.2.0 | Preferences |
| exifinterface | 1.4.1 | Photo orientation |

No image-loading library: photos are decoded through `BitmapFactory` in `core/photo`,
which is enough for the handful a screen shows at once.

A debug build recreates the database on a schema change (`fallbackToDestructiveMigration`),
which is also how a change to the preloaded dictionaries reaches an installed debug build.
A release build has no such fallback and moves between versions by migrations; Room exports
the schema of every version to `app/schemas` for them to be written and tested against.

Version 1 went out with the first release, so `1.json` is the contract with every installed
copy: no entity changes without `VERSION` going up, and every version after it needs a
`Migration` in `data/db/Migrations.kt` — without one the release build throws on opening a
database it cannot bring forward.

**What has not shipped is not a version yet.** While work goes on between releases the
number may climb several times over; when the next release is cut, those steps are gathered
into the one migration that leads from the last released version, and the schemas of the
steps in between are deleted along with them. Nobody's phone is sitting on them, and a
migration path nobody can be on is a path nobody can test. What has gone out is fixed for
good — its schema stays in `app/schemas` and its migration stays where it is. The statements there are copied from the exported schema
of the version they lead to, down to the index names, because that is what Room validates
against on the next open. `MigrationTest` runs each of them against a real database of the
version it starts from, with rows in it, and reopens the result through Room; the exported
schemas travel with the instrumented tests as assets for it. Preloaded dictionary rows are
part of the same bargain: a new currency or favourite, or a change to a row that is already
there — a category that starts counting quantity — reaches an installed release only if a
migration writes it, since `onCreate` has already run and `onOpen` seeds only empty tables.
Such a migration finds the row by `name_key` rather than by name: the name is in whatever
language the app was first opened in, and a person may have rewritten it.

A debug build carries `applicationIdSuffix = ".debug"`, so it installs beside a release one
instead of refusing to replace it. What tells them apart in the launcher is `src/debug/res`:
the app name, and the colour of the icon's background.

The release build is **minified** (R8): unreachable code dropped, the rest optimized. That
is where the size goes — the APK comes out several times smaller, and the `dex` inside it by
more than that — since Compose, Material 3, Hilt and Room bring far more with them than an
app of this size reaches into. Room, Hilt and kotlinx.serialization each ship the rules
their generated code needs.

**Renaming is off** (`-dontobfuscate`). Next to the shrinking it saves little: a few per
cent of the APK, a tenth or two of the `dex` inside it, which compresses back down again. Against that it puts at risk everything this app finds by name at run time — the
navigation routes, which are `@Serializable` classes nothing calls, and the dictionary
strings resolved through `getIdentifier` — and the code is public in any case, so there is
nothing for renaming to hide. Line numbers and file names are kept with it, so a crash
report reads as it stands. R8 still writes `mapping.txt`; it is what untangles the frames
inlining moved, it belongs to the one build it came out of, and it is kept with the release
rather than published.

**Resource shrinking stays off** — preloaded dictionary names are resolved by string through
`getIdentifier`, which no shrinker can see, and they survive today only because
`DefaultData` also names each of them through `R.string`.

Releasing raises `versionCode` and `versionName` in a commit of its own, which is tagged and
built from. The APK comes out as `jourjour-<versionName>.apk`, so a file that has left the
build folder still says what it is. Minifying writes `mapping.txt`, the only key to the stack traces that build will
produce; it is kept with the release, since the next build overwrites it.

### Permissions

Fine and coarse location, both optional and requested at the moment of use.
`android.hardware.location` is declared as not required. `INTERNET` is declared for one
button: fetching an exchange rate, which sends two currency codes and a date and receives a
number. Nothing a person enters ever leaves the device, and everything else in the app works
with the network off. **The camera permission is
deliberately not declared** — capture goes through `ACTION_IMAGE_CAPTURE` and a
`FileProvider` link granted for one file, which needs none. Reading a picked photo goes
through the Photo Picker, which needs none either.

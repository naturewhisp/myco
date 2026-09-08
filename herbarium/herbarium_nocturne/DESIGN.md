---
name: Herbarium Nocturne
colors:
  surface: '#171309'
  surface-dim: '#171309'
  surface-bright: '#3e392d'
  surface-container-lowest: '#120e05'
  surface-container-low: '#1f1b11'
  surface-container: '#241f15'
  surface-container-high: '#2e291e'
  surface-container-highest: '#393429'
  on-surface: '#ebe1d0'
  on-surface-variant: '#c5c8ba'
  inverse-surface: '#ebe1d0'
  inverse-on-surface: '#353024'
  outline: '#8f9286'
  outline-variant: '#45483e'
  surface-tint: '#b8ce97'
  primary: '#b8ce97'
  on-primary: '#25350d'
  primary-container: '#93a874'
  on-primary-container: '#2c3d14'
  inverse-primary: '#526437'
  secondary: '#e9c266'
  on-secondary: '#3f2e00'
  secondary-container: '#745800'
  on-secondary-container: '#f8d073'
  tertiary: '#ffb689'
  on-tertiary: '#512300'
  tertiary-container: '#dc8e5b'
  on-tertiary-container: '#5c2900'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#d4eab1'
  primary-fixed-dim: '#b8ce97'
  on-primary-fixed: '#112000'
  on-primary-fixed-variant: '#3a4c22'
  secondary-fixed: '#ffdf99'
  secondary-fixed-dim: '#e9c266'
  on-secondary-fixed: '#251a00'
  on-secondary-fixed-variant: '#5a4300'
  tertiary-fixed: '#ffdbc8'
  tertiary-fixed-dim: '#ffb689'
  on-tertiary-fixed: '#311300'
  on-tertiary-fixed-variant: '#70370b'
  background: '#171309'
  on-background: '#ebe1d0'
  surface-variant: '#393429'
typography:
  display-lg:
    fontFamily: Newsreader
    fontSize: 3rem
    fontWeight: '400'
    lineHeight: 3.5rem
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Newsreader
    fontSize: 2.25rem
    fontWeight: '400'
    lineHeight: 2.75rem
    letterSpacing: -0.01em
  headline-lg:
    fontFamily: Newsreader
    fontSize: 2rem
    fontWeight: '400'
    lineHeight: 2.5rem
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Newsreader
    fontSize: 1.75rem
    fontWeight: '400'
    lineHeight: 2.25rem
    letterSpacing: 0em
  headline-md:
    fontFamily: Newsreader
    fontSize: 1.5rem
    fontWeight: '500'
    lineHeight: 2rem
  headline-sm:
    fontFamily: Newsreader
    fontSize: 1.25rem
    fontWeight: '500'
    lineHeight: 1.75rem
  body-lg:
    fontFamily: Newsreader
    fontSize: 1.125rem
    fontWeight: '400'
    lineHeight: 1.75rem
  body-md:
    fontFamily: Newsreader
    fontSize: 1rem
    fontWeight: '400'
    lineHeight: 1.625rem
  body-sm:
    fontFamily: Newsreader
    fontSize: 0.875rem
    fontWeight: '400'
    lineHeight: 1.375rem
  label-lg:
    fontFamily: Inter
    fontSize: 0.875rem
    fontWeight: '600'
    lineHeight: 1.25rem
    letterSpacing: 0.02em
  label-md:
    fontFamily: Inter
    fontSize: 0.75rem
    fontWeight: '500'
    lineHeight: 1rem
    letterSpacing: 0.04em
  label-sm:
    fontFamily: Inter
    fontSize: 0.6875rem
    fontWeight: '500'
    lineHeight: 0.875rem
    letterSpacing: 0.06em
  caption-taxonomic:
    fontFamily: Newsreader
    fontSize: 0.8125rem
    fontWeight: '400'
    lineHeight: 1.125rem
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  none: 0rem
  2xs: 0.125rem
  xs: 0.25rem
  sm: 0.5rem
  md: 0.75rem
  base: 1rem
  lg: 1.5rem
  xl: 2rem
  2xl: 3rem
  3xl: 4rem
  gutter-mobile: 1rem
  gutter-tablet: 1.5rem
  gutter-desktop: 2rem
  margin-mobile: 1rem
  margin-tablet: 2rem
  margin-desktop: 3.5rem
---

## Brand & Style

This design system translates the rigorous scientific poetry of a classic botanical herbarium into a nocturnal, high-utility digital environment. Rooted in tactile field study, taxonomy, and mycology, the visual narrative avoids sterile technical dark modes in favor of an organic, warm-toned archive observed under soft lantern light.

The emotional atmosphere is quiet, contemplative, and deeply authoritative. It evokes the feeling of pressed specimens, aged rag paper preserved in shadow, and precise botanical inkings. The aesthetic language pairs academic sobriety with organic warmth: opaque matte card stock, razor-thin framing rules, and high-luminance botanical pigment accents that emerge legible and vivid against deep earthy undertones. 

No translucent blurs, no glowing glassmorphic surfaces, and no synthetic blue-cast grays are permitted. Depth is established solely through subtle surface stratification, deliberate ink hierarchy, and disciplined ruled dividers.

## Colors

The palette directly implements the nocturnal botanical standards specified in operational sections 4 and 5. The tonal base is anchored in peat, umber, and dried earth, balanced with botanical extract tones calibrated for dark-field legibility.

### Canvas & Surfaces
- **NightBase (`#221E18`)**: Primary canvas. A dense, warm soil brown that replaces harsh digital black.
- **NightSurface (`#2E2820`)**: Elevated panels, cards, sheets, and modular containers. Completely opaque.
- **NightSurfaceRaised (`#383127`)**: Modals, dropdown sheets, and active control backgrounds.

### Linework & Partitioning
- **NightRule (`#5C5245`)**: Primary structural borders, table outlines, and prominent separation rules.
- **NightRule2 (`#3E372E`)**: Subdued interior dividers, subtle gridlines, and quiet boundaries.

### Inks & Typography
- **NightInk (`#EDE3D2`)**: Highest contrast botanical parchment ink. Used for headlines, critical data, and key interactive labels.
- **NightInkSoft (`#B6A891`)**: Secondary reading ink. Used for long-form narrative body, secondary labels, and descriptive taxonomic attributes.
- **NightInkVague (`#7F725F`)**: De-emphasized metadata, timestamps, and disabled elements.

### Botanical Accents (Calibrated Night Luminance)
- **Lichene Notte (`#93A874`)**: Primary accent. Applied to active states, healthy status tags, confirmed identifications, and primary CTAs.
- **Salvia Notte (`#6B7A55`)**: Subdued secondary foliage tone for secondary badges, filters, and structural accents.
- **Ocra Notte (`#DCB65C`)**: Secondary accent. Applied to taxonomic markers, highlights, alerts, and pending observations.
- **Terracotta Notte (`#C67C4B`)**: Tertiary accent. Applied to spore categories, fungal typologies, and warm indicators.
- **Ruggine Notte (`#B85A45`)**: Critical alerts, toxic/hazard classifications, and destructive actions.
- **Indaco Meteo (`#8AA3B5`)**: Ambient meteorological telemetry, humidity, barometric readings, and moisture data.

## Typography

The typographic hierarchy juxtaposes classical botanical monograph printing with legible, functional fieldwork data.

- **Headlines & Taxonomic Names**: Built entirely with `Newsreader`. Titling embraces traditional editorial weights and proportional serif figures. Taxonomic binomials (genus and species) must strictly render in `Newsreader` italic.
- **Body Text**: Rendered in `Newsreader` at generous line-heights, providing comfortable sustained reading akin to an archival field compendium.
- **Labels, Telemetry & Controls**: Supported by `Inter` for extreme clarity at diminutive scales (field inputs, coordinates, micro-tags, and button text). Labels leverage slight letter-spacing to offset dark background irradiation.

## Layout & Spacing

The layout model is anchored by a structured, rhythmic grid that reflects archival herbarium specimen sheets. Content organizes cleanly into demarcated folios and plates.

### Grid Architecture
- **Desktop (1024px and up)**: 12-column fluid grid, 2rem (32px) gutters, 3.5rem (56px) outer margins. Maximum layout constraint: 1360px.
- **Tablet (640px to 1023px)**: 8-column fluid grid, 1.5rem (24px) gutters, 2rem (32px) outer margins.
- **Mobile (up to 639px)**: 4-column fluid grid, 1rem (16px) gutters, 1rem (16px) outer margins.

### Rhythmic Discipline
Vertical cadence strictly honors multiples of `0.25rem` (4px baseline unit). Archival data cards use compact internal padding (`0.75rem` to `1.25rem`) to maximize information density while maintaining distinct breathing room between major modular sections (`2rem` to `3rem`).

## Elevation & Depth

This system intentionally rejects synthetic elevation models: no standard drop shadows, no directional light casts, and strict prohibition of frosted glassmorphism or backdrops.

### Tonal Stratification
Depth is achieved through the architectural stacking of opaque, warm paper tones:
1. **Base Layer (`#221E18`)**: Root backdrop of the application.
2. **Surface Layer (`#2E2820`)**: Standalone specimens, observation folios, and analytical cards.
3. **Elevated Layer (`#383127`)**: Flyout drawers, context menus, and elevated inspector trays.

### Linear Demarcation (Filetti)
Every boundary is reinforced by physical hairline borders:
- Use `NightRule2` (`#3E372E`, 1px solid) for standard containment.
- Use `NightRule` (`#5C5245`, 1px solid) for interactive card bounds, table headers, and focused panels.
- For active modals or floating drawers, add an ambient edge seal: `0 0 0 1px #5C5245, 0 12px 28px rgba(18, 15, 12, 0.65)`. Shadows are deep umber, wide-diffusion, and devoid of cool or blue undertones.

## Shapes

The shape grammar is sober and architectural, reflecting historic cardstock mounts and laboratory slide plates.

- **Geometry**: The base roundedness is `rounded-sm` (0.25rem / 4px). This applies to buttons, input fields, tags, specimen panels, and cards.
- **Corner Caps**: Internal badges, status chips, and code tags also utilize `0.25rem` (4px). Pure circles are reserved strictly for circular avatars, microscopic previews, and radio buttons.
- **Precision**: Rounded corners should feel crisp and trimmed with a razor, never bubbly or rubbery.

## Components

### Buttons & Action Controls
- **Primary Button**: Solid fill in Lichene Notte (`#93A874`), foreground in deep NightBase (`#221E18`), font weight 600 in `Inter`. Rounded `0.25rem`. Hover shifts fill to a luminous moss tint (`#A3B884`).
- **Secondary Button**: NightSurface (`#2E2820`) background with a 1px border in NightRule (`#5C5245`). Foreground in NightInk (`#EDE3D2`). Hover elevates background to `#383127` and border to Lichene Notte.
- **Ghost/Tertiary**: Transparent background, NightInkSoft (`#B6A891`) text, hover introduces `rgba(220, 182, 92, 0.08)` fill and Ocra Notte text.

### Badges & Taxonomic Chips
- Thin, bounded labels using `label-sm` in `Inter`.
- Composed of an opaque surface tinted with 12% opacity of the accent color over NightSurface (`#2E2820`), bound by a 1px border of that accent at 40% opacity.
- **Specimen Type / Lichen**: Accent Lichene Notte (`#93A874`).
- **Taxon / Family**: Accent Ocra Notte (`#DCB65C`).
- **Substrate / Edaphic**: Accent Terracotta Notte (`#C67C4B`).
- **Weather / Humidity**: Accent Indaco Meteo (`#8AA3B5`).

### Input Fields
- Background in NightBase (`#221E18`), outlined in 1px NightRule (`#5C5245`).
- Font: `Inter` 14px in NightInk (`#EDE3D2`); placeholder text in NightInkVague (`#7F725F`).
- Focus state: Border transitions to Lichene Notte (`#93A874`) with an outline offset of 0 (no loud glow rings, only a razor-sharp 1px accent rule).

### Cards & Specimen Folios
- Solid NightSurface (`#2E2820`) fill with a 1px perimeter rule in NightRule2 (`#3E372E`).
- Card headers feature botanical titles in `Newsreader` and are separated from content bodies by a 1px horizontal rule in NightRule2.
- Interactive cards subtly shift their border to NightRule (`#5C5245`) on hover.

### Selection Controls
- **Checkboxes**: Square with `0.125rem` (2px) border radius. Unselected: NightBase fill, 1px NightRule border. Selected: Lichene Notte fill with NightBase mark.
- **Radio Buttons**: Concentric circle with NightRule boundary. Selected state displays an inner pip of Ocra Notte (`#DCB65C`).

### Meteorological & Analytical Telemetry Strips
- Specialized modular strips presenting atmospheric data (spore conditions, moisture, canopy coverage).
- Built on NightSurface with vertical dividing hairlines in NightRule2 (`#3E372E`). Metric readouts pair `Inter` numerical values with `Indaco Meteo` (`#8AA3B5`) micro-icons and labels.
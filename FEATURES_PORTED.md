# iOS Features Ported to Android

## Summary
Complete port of iOS BuddyApp home functionality improvements to Android, including:
- Help request flow (intentions button)
- Community live section with real-time activity
- Buddy matching integration

## Features Implemented

### 1. Help Request Flow (Intentos / Intentions)
**File**: `features/home/HelpRequestFlow.kt`

- **CategoryPickerView**: Select help category with icons
  - Transport, Accommodation, Food, Other
  - Optional description field
  - Real-time buddy count display
  
- **SearchingView**: Real-time buddy search with progress
  - Loading indicator with animated message
  - Expandable search messaging (expanding search...)
  - Cancel option
  
- **MatchedBuddyView**: Display matched buddy profile
  - Avatar display
  - Buddy name and rating
  - Accept/Decline actions

### 2. Comunidad Viva (Live Community)
**File**: `features/home/CommunityLiveSection.kt`

Shows real-time community activity in two modes:

#### Local Activity (when destination has recent help)
- Shows up to 3 recent buddy help instances
- Format: "Juan ayudó a un viajero · hace 2h"
- Avatar + buddy name + timeago
- Prioritized over global pulse

#### Global Pulse (fallback when no local activity)
- 3 types of pulse events:
  - `traveling`: "N viajeros están en [city]"
  - `ready`: "[City] · N buddies listos para ayudar"
  - `helped`: "[City] · un buddy ayudó a un viajero · hace Xh"

#### Features
- Dividers between rows
- Automatic timeago formatting (hace 1h, hace 2d, etc.)
- Responsive layout
- Card container with border and shadow

### 3. API Models
**File**: `core/data/model/ApiModels.kt`

New models added:
```kotlin
data class ApiMatchingStatus      // Search state, buddy position/total
data class ApiMatch               // Match object with traveler/buddy refs
data class ApiHelpRequest         // Help request payload
data class ApiRecentHelp          // Recent help in a destination
data class ApiPulseItem           // Global pulse event
data class ApiPulseResponse       // Pulse response wrapper
```

### 4. HomeApi Endpoints
**File**: `features/home/data/HomeApi.kt`

New endpoints:
```kotlin
suspend fun recentHelpByPlace(placeId: String): List<ApiRecentHelp>
suspend fun communityPulse(): ApiPulseResponse
```

### 5. HomeViewModel Updates
**File**: `features/home/HomeViewModel.kt`

Enhanced state:
```kotlin
data class HomeState(
    // ... existing fields ...
    val recentHelp: List<ApiRecentHelp> = emptyList()
    val communityPulse: List<ApiPulseItem> = emptyList()
    val isLoadingCommunity: Boolean = false
)
```

New methods:
- `loadCommunityLive()`: Load local activity or global pulse
- `formatTimeAgo(isoDate)`: Format timestamps like iOS

### 6. InicioScreen Integration
**File**: `features/home/InicioScreen.kt`

- Added CommunityLiveSection composable
- Integrated with HomeViewModel state
- Positioned between composer and story feed
- Matches iOS layout and styling

## Architecture Alignment

### Traveler-First Model ✓
- Guest sessions automatically created on first action
- No breaking changes to existing auth flow

### SSE Integration ✓
- MatchingApi already supports real-time via SSE
- Can be enhanced for community pulse updates

### Localization ✓
- All Spanish text strings use hardcoded messages
- Ready for string resource extraction (future phase)

### Design System ✓
- Uses BuddyColor, BuddyType, Spacing, Radius from theme
- Consistent with Material 3 + Buddy branding

## Testing Checklist

- [ ] Community section loads with real data
- [ ] Timeago formatting matches iOS behavior
- [ ] Category picker form works end-to-end
- [ ] Search state shows loading, expand, completion
- [ ] Pulse/recent help display switches correctly
- [ ] Buddy count displays accurately
- [ ] All icons render correctly
- [ ] Layout responsive on different screen sizes

## Known Limitations / Future Work

1. **Localization**: Spanish hardcoded (future: extract to strings.xml)
2. **Animations**: Basic transitions, iOS has polish animations
3. **Push Notifications**: Community pulse could trigger notifications
4. **Accessibility**: Material 3 provides baseline, review IDs
5. **A/B Testing**: No feature flags yet for rollout
6. **Analytics**: No event tracking for help requests

## Differences from iOS

| Aspect | iOS | Android |
|--------|-----|---------|
| Category Icons | SF Symbols | Material Icons |
| Pulse Loading | Skeleton cards | Circular progress |
| SSE Stream | URLSession.bytes | OkHttp callback flow |
| Time Formatting | DateComponentsFormatter | Manual calculation |
| Keyboard | Automatic focus | Manual focus state |

## Integration Notes

- **Matching**: Reuses existing MatchingViewModel for buddy search
- **Navigation**: Works with existing tab routing
- **State**: Follows MVVM + StateFlow pattern
- **Networking**: Uses buddy-core APIs via Retrofit

## Code Statistics

| File | LOC | Type |
|------|-----|------|
| CommunityLiveSection.kt | ~180 | Composable |
| HelpRequestFlow.kt | ~280 | Composable |
| HomeViewModel.kt | +60 | ViewModel (additions) |
| ApiModels.kt | +80 | Data Models |
| HomeApi.kt | +10 | API Interface |
| InicioScreen.kt | +15 | Screen (additions) |

**Total Addition**: ~625 lines

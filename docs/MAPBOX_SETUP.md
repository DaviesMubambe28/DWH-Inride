# Mapbox configuration

The app fails closed when Mapbox is not configured. It shows **Configuration required** and does not substitute mock routes, places, coordinates, or fares.

## 1. Create the tokens

1. Create or sign in to a Mapbox account at <https://account.mapbox.com/>.
2. Open **Tokens** at <https://console.mapbox.com/account/access-tokens/>.
3. Create a public token for the Android apps. Keep the public scopes needed by Maps, Search/Geocoding, and Directions. Copy the value beginning with `pk.`.
4. Create a second public token for server routing. Keeping it separate makes rotation and usage tracking safer. Do not add browser URL restrictions because Supabase Edge Function egress has no browser origin.
5. Optionally create a separate public token for the admin web app and restrict it to the production and local web origins once those origins are final.

Never put a Mapbox secret token beginning with `sk.` in an Android APK or a `NEXT_PUBLIC_` variable.

## 2. Configure Android

Add this to the ignored root `secrets.properties` file:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-publishable-key
MAPBOX_PUBLIC_TOKEN=pk.your_android_public_token
```

Then rebuild both Android apps.

## 3. Configure the admin web app

Add this to `sites/dhw-inride-admin/.env.local`:

```dotenv
NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN=pk.your_web_public_token
```

Keep the existing Supabase variables in that file and restart `npm run dev` after changing it.

## 4. Configure local server routing

Create the ignored file `supabase/functions/.env` from `supabase/functions/.env.example`:

```dotenv
MAPBOX_SERVER_TOKEN=pk.your_server_routing_token
```

The local Supabase stack reads this exact file when it starts. Restart the stack after changing the token.

## 5. Configure hosted server routing

Set the production Edge Function secret from the project root:

```powershell
.\node_modules\.bin\supabase.cmd secrets set MAPBOX_SERVER_TOKEN=pk.your_server_routing_token
```

Then deploy the function:

```powershell
.\node_modules\.bin\supabase.cmd functions deploy request-trip
```

## Variables summary

| Runtime | File or secret | Variable |
| --- | --- | --- |
| Android passenger/rider | `secrets.properties` | `MAPBOX_PUBLIC_TOKEN` |
| Admin Next.js app | `sites/dhw-inride-admin/.env.local` | `NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN` |
| Local Edge Functions | `supabase/functions/.env` | `MAPBOX_SERVER_TOKEN` |
| Hosted Edge Functions | Supabase Function Secrets | `MAPBOX_SERVER_TOKEN` |

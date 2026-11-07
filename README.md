# IP Address Hider

Client-side Fabric mod for Minecraft 1.21.11 that keeps server addresses and names off your screen while streaming or recording.

## Features

- **Lock button** next to the address and name fields in *Add/Edit Server* and *Direct Connection*.
- **Random-length masking:** a locked address is shown as 7–40 asterisks, re-rolled every time the screen opens, so not even its length gives it away. While you type, each character shows as one asterisk.
- **Hidden server names** in the server list, re-rolled (0–40 asterisks) on every refresh.
- **Streamer Mode:** hides every address and name at once, without touching your individual locks. Turn it off and everything is back the way it was.
- **Twitch integration (optional):** with *Only while live*, Streamer Mode only kicks in while your channel is live.

Settings are in Mod Menu.

## Notes

- With *Only while live* enabled, the mod checks your Twitch status every 60 seconds, so addresses can stay visible for up to a minute after you go live. For maximum safety, leave that option off.
- Anything the server itself displays (tab list, scoreboard, chat) can't be hidden by this mod.
- Hiding only changes what is drawn on screen. The address Minecraft connects to is never modified.

## Twitch and privacy

Without a connected Twitch account the mod makes no network requests. Connecting uses Twitch's device code login (no password is entered in Minecraft) and requests no permissions. The login is stored separately in `config/ipaddresshider-twitch.json`. *Disconnect Twitch* revokes it and deletes that file; the app may still be listed under Twitch Settings > Connections, where you can remove it as well. See [NOTICE](NOTICE) for details.

## Requirements

- Minecraft 1.21.11
- Fabric Loader 0.20.1+
- Fabric API
- Mod Menu (optional, for the settings screen)

## License

Apache-2.0

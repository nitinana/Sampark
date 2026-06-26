1. The App's Core Philosophy: "Install and Go" with a Safety Net

The app will largely work automatically in the background after a simple initial setup. There won't be many complex menus or decisions for the user to make.

Key Features & How They Work:

a. Initial Setup (One-Time, with your assistance):

Purpose: When the app is first opened, it will ask for necessary permissions (to read/write contacts, monitor calls, and display information over other apps).

User Experience: The screen will be very simple and entirely in Marathi. It will explain what the app does in straightforward terms, with one large "Start" button. You (the helper) will verbally guide them to tap "Allow" for each system permission prompt that appears.

b. Converting Existing English Contact Names to Marathi (Automated):

What it does: After initial setup, the app will automatically scan all existing contacts in the phone. If a contact has an English name, the app will translate it to Marathi and automatically update the contact name in the phone's built-in Contacts app.

Seamless Experience: The user won't see pop-ups or need to confirm each name. This happens silently in the background.

Technology: It will use an on-device translation model (like Google's ML Kit), meaning it works even without an internet connection once the app is set up.

c. Saving New Contacts with Marathi Names (Simplified Options):

Option 1 (From Call Disconnect - Primary Method): This is the easiest way for new contacts to get Marathi names. (See point 'd' below).

Option 2 (Manual Add - if needed):

If someone wants to add a new contact from scratch, they can open our app's icon.

Our app will show a very simple screen with fields for "Number" and "Name (Marathi)."

As an English name is typed (or spoken, if speech-to-text is used), the app will automatically suggest the Marathi translation in the Marathi name field.

They just tap one large "Save" button, and the contact is saved directly to the phone's contacts with the Marathi name. It will not open the phone's default "add contact" screen, simplifying the process.

d. Identifying Unknown Callers & Saving (Automated & Streamlined):

What it does: When an unknown number calls and disconnects, the app will automatically try to find the caller's name using an online service (similar to Truecaller).

Translation & Overlay: It will then translate that name to Marathi and display it prominently in a small pop-up message on the screen (an "overlay").

One-Tap Save: This overlay will have one large "Save Contact" button. If tapped, the app will automatically save the number and the Marathi name directly to the phone's contacts. No extra steps or opening the native Contacts app is needed.

Silence & Auto-Dismiss: The overlay will disappear automatically after a few seconds if not touched.

e. The "Undo" / Rollback Feature (Your Safety Net):

What it is: This is crucial. Our app will always remember the original English names it translated.

How to Use: If you ever find the Marathi names confusing, or prefer the original English names back, simply tap our app's icon.

The app will open to a very simple screen with one very large, clear button: "Bring Back English Names" (in Marathi).

Tapping this button will automatically revert all the names the app changed back to their original English versions.

2. Technology & Development Considerations (for you, the developer):

Platform: Primarily Android. iOS is much more complex for private, non-App Store distribution.
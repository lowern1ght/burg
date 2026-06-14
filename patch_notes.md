## 🏘️ Once Upon a Town v0.0.7 [Beta]
### Building catalog
- Inside the construction panel, there is a new system to let you preview the render of the building you selected. If the building got ever build in the village you will see it fully rendered but if the builder never got build, it will be locked
- You can click on the expend button to maximize the view and scroll throughout all the levels from the building, time to collect them all !
- A catalog is tied to one village, it will be changed later
### Big GUI improvements
- The right panel tabs are now places on the left, giving more space to the interface, each section is represented by a simple icon
- Moved the widget icons to the bottom left of the management panel
- Simplified the era progress widget, it is now focused on the elements you have to fulfil to pass to the next era. The single/cross era choice is now harmonized sharing the same 'card' look
- Reworked the upgrade interface making it easier to understand by showing the 'upgrades bar' on hover and placing the unlocked recipes into a proper grid
- Removed various mention to 'settlement', 'village' or orientations in the interface, making it cleaner and with less inconsistencies
### Village management
- You can now unlock a new builder at the era 2, making it way easier to developer your village building new structures while upgrading some at the same time
- Fixed a ton of issues with some elements not updating in the interface (building, resources, construction spot and so on). Most of the elements from the GUI should update live when you are checking the panels, making the reading and managing experience smoother
- Reworked the tooltips to make all of them shorter and easier to understand, especially in the construction panel
### Gameplay loop
- Improved the progression by making a lot more buildings tied to each other, you need to build more specific buildings in order to unlock the 'upgraded' versions, you will need crops to unlock animals and more production buildings to unlock craftmanship building
- Added way more criteria to the era progress, making each one way more specific and making the player following a clear direction
- The building unlocked for the next era are directly present in the building catalog, helping you to track and anticipate your progress into the your orientation branch
- There is far less quest and they are less invasive, decided to pause this feature until I find a good way of implementing it
### More customization
- The era are now 100% data driven
- The builder AI is now 100% data driven
- You can now sort all the buildings together the way you want them to display thanks to a simple config json you have to manage
### New buildings 
- Wild bee
- Bee field
- Beekeeper (with 6 upgrades)
- Lone Garden
- Lone Place
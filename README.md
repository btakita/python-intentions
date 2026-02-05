# Python Intentions Jetbrains Plugin

Pycharm plugin with various intentions.

## Installation

To install the plugin, follow these steps:
1. Open PyCharm.
2. Go to `File` > `Settings` (or `PyCharm` > `Preferences` on macOS).
3. Navigate to `Plugins`.
4. Click on the `Marketplace` tab.
5. Search for `Python Intentions`.
6. Click `Install` and then `Restart PyCharm` when prompted.

## Intention Actions

- `Optional[A]` ↔ `A | None` (PEP 604)
- `Union[A, B, C]` ↔ `A | B | C` (PEP 604)
- Add/Remove top-level names to/from `__all__`

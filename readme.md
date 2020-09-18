# Moen Flo (and Smart Water Detector) for Hubitat

## License
Moen Flo for Hubitat by David Manuel is licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0). 
Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

## Installation
1. Go to "Drivers Code" in Hubitat
2. Click New Driver
3. Click Import
4. Paste the install url into the text field: https://raw.githubusercontent.com/dacmanj/hubitat-moenflo/master/moenflo.groovy for the Moen Flo valve, or https://raw.githubusercontent.com/jlaughter/hubitat-moenflo/master/moenflodetector.groovy for the Smart Water Detector. 
5. Click Import
6. Click Save
7. Go to Devices
8. Click "Add Virtual Device"
9. Enter a Name and Label for your Moen Flo or Smart Water Detector
10. For the Type select Moen Flo, or Moen Flo Smart Water Detector
11. Click "Save Device"
12. Enter your MeetFlo.com username and password
13. IF, you have multiple Flo devices, enter the Device Id of the device you'd like to use (the alphanumeric code found at https://user.meetflo.com/settings/devices)
14. Click "Save Preferences"
15. If installation succeeds, you will see valve status and other attributes appear in Current States. If you don't, check your logs.

## Usage
- The valve capability works just like any other valve and can be opened or closed by HSM (e.g. when water is detected)
- The mode (home, away and sleep) can be set using the device buttons or by "pushing" one of the three buttons in an automation rule. Button 1 is for "Home" mode, Button 2 is for "Away" mode, and Button 3 is for "Sleep" mode.
- The API requires a timeout value for "Sleep" mode to return to another mode. The default is to return to Home mode after 120 minutes, but can be changed in the preferences.
- The Manual Health Test button performs a manual health test. The last result for a hubitat initiated health test will appear in the attributes on the next poll after the test is complete.
- The Smart Water Detector's virtual device is a read-only temperature, humidity, and water presence sensor, and reports battery life.  No commands are needed for this device.

/**
 * Moen Flo Water Detector for Hubitat 
 * Based on Moen Flo for Hubitat by David Manuel https://github.com/dacmanj/hubitat-moenflo
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 * 2020-09-17 v0.0.1 - Modified v0.1.07-alpha to support Moen Flo Water Detectors - Jeffrey Laughter
 * 
 */

metadata {
    definition (name: "Moen Flo Water Detector", namespace: "jlaughter", author: "Jeffrey Laughter") {
        capability "RelativeHumidityMeasurement"
        capability "TemperatureMeasurement"
        capability "WaterSensor"
        capability "Battery"

        command "logout"
        command "pollMoen"

        attribute "temperature", "number"
        attribute "humidity", "number"
        attribute "battery", "number"
        attribute "water", "enum", ["wet", "dry"]
        attribute "updated", "string"
        attribute "rssi", "number"
        attribute "ssid", "string"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"   
    }

    preferences {
        input(name: "username", type: "string", title:"User Name", description: "Enter Moen Flo User Name", required: true, displayDuringSetup: true)
        input(name: "password", type: "password", title:"Password", description: "Enter Moen Flo Password (to set or change it)", displayDuringSetup: true)
        input(name: "mac_address", type: "string", title:"Device Id", description: "Enter Device Id from MeetFlo.com (if you have multiple devices)", required: false, displayDuringSetup: true)
        input(name: "revert_mode", type: "enum", title: "Revert Mode (after Sleep)", options: ["home","away","sleep"], defaultValue: "home")
        input(name: "polling_interval", type: "number", title: "Polling Interval (in Minutes)", range: 5..59, defaultValue: "10")
        input(name: "revert_minutes", type: "number", title: "Revert Time in Minutes (after Sleep)", defaultValue: 120)    
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def logsOff(){
    device.updateSetting("logEnable", false)
    log.warn "Debug Logging Disabled..."
}

def logout() {
    state.clear()
    unschedule()
    device.updateDataValue("token","")
    device.updateDataValue("device_id","")
    device.updateDataValue("location_id","")
    device.updateDataValue("user_id","")  
    device.updateDataValue("encryptedPassword", "")
    device.removeSetting("username")
    device.removeSetting("mac_address")
}

def updated() {
    configure()
    if (state.configured) pollMoen()
    if (logEnable) runIn(1800,logsOff)
}

def installed() {
    runIn(1800,logsOff)
}

def unschedulePolling() {
    unschedule(pollMoen)
}

def schedulePolling() {
    unschedule(pollMoen)
    def pw = device.getDataValue("encryptedPassword")
    if (polling_interval != "None" && pw && pw != "") {
        schedule("0 0/${polling_interval} * 1/1 * ? *", pollMoen)
    }
}

def pollMoen() {
    if (logEnable) log.debug("Polling Moen")
    getDeviceInfo()
    getLastAlerts()
}

def getUserInfo() {
    def user_id = device.getDataValue("user_id")
    if (mac_address) { log.debug "Getting device id for: ${mac_address}"}
    else { log.debug "Defaulting to first device found." }
    def uri = "https://api-gw.meetflo.com/api/v2/users/${user_id}?expand=locations,alarmSettings"
    def response = make_authenticated_get(uri, "Get User Info")
    device.updateDataValue("location_id", response.data.locations[0].id)
    response.data.locations[0].devices.each {
        if(it.macAddress == mac_address || !mac_address || mac_address == "") {
            device.updateDataValue("device_id", it.id)
            device.updateSetting("mac_address", it.macAddress)
            if(logEnable) log.debug "Found device id: ${it.id}"
        }
    }
}

def getDeviceInfo() {
    def device_id = device.getDataValue("device_id")
    if (!device_id || device_id == "") {
        log.debug "Cannot complete device info request: No Device Id"
    } else {
        def uri = "https://api-gw.meetflo.com/api/v2/devices/${device_id}"
        def response = make_authenticated_get(uri, "Get Device")
        def data = response.data
        sendEvent(name: "temperature", value: round(data?.telemetry?.current?.tempF, 0), unit: "F")
        sendEvent(name: "humidity", value: round(data?.telemetry?.current?.humidity, 0), unit: "%")
        sendEvent(name: "battery", value: round(data?.battery?.level, 0), unit: "%")
        def water_state = data?.fwProperties?.telemetry_water
        def WATER_STATES = [1: "wet", 2: "dry"]
        if (water_state) {
            sendEvent(name: "water", value: WATER_STATES[1])}
        else { sendEvent(name: "water", value: WATER_STATES[2])}            
        sendEvent(name: "updated", value: data?.telemetry?.current?.updated)
        sendEvent(name: "rssi", value: data?.fwProperties?.telemetry_rssi)
        sendEvent(name: "ssid", value: data?.fwProperties?.wifi_sta_ssid)
    }
}

def getLastAlerts() {
    def device_id = device.getDataValue("device_id")
    if (!device_id || device_id == "") {
        log.debug "Cannot fetch alerts: No Device Id"
    } else {
        def uri = "https://api-gw.meetflo.com/api/v2/alerts?isInternalAlarm=false&deviceId=${device_id}"
        def response = make_authenticated_get(uri, "Get Alerts")
        def data = response.data.items
        if (data) {
            sendEvent(name: "lastEvent", value: data[0]?.displayTitle)
            sendEvent(name: "lastEventDetail", value: data[0].displayMessage)
            sendEvent(name: "lastEventDateTime", value: data[0].createAt)
        }
    }
}

def round(d, places = 2) {
    try { return (d as double).round(places) }
    catch (Exception e) { return (null) }
}

def make_authenticated_get(uri, request_type, success_status = [200, 202]) {
    def token = device.getDataValue("token")
    if (!token || token == "") login();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:] 
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", device.getDataValue("token"))
    
        try {
            httpGet([headers: headers, uri: uri]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.debug "${request_type} Failed (${response.status}): ${response.data}"
                    login()
                }
              }
        }
        catch (Exception e) {
            log.debug "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Refreshing token..."
                login()
            }
        }
        tries++

    }
    return response
}

def make_authenticated_post(uri, body, request_type, success_status = [200, 202]) {
    def token = device.getDataValue("token")
    if (!token || token == "") login();
    def response = [:];
    int max_tries = 2;
    int tries = 0;
    while (!response?.status && tries < max_tries) {
        def headers = [:] 
        headers.put("Content-Type", "application/json")
        headers.put("Authorization", device.getDataValue("token"))
    
        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { resp -> def msg = ""
                if (logEnable) log.debug("${request_type} Received Response Code: ${resp?.status}")
                if (resp?.status in success_status) {
                    response = resp;
                }
                else {
                    log.debug "${request_type} Failed (${resp.status}): ${resp.data}"
                }
            }
        }
        catch (Exception e) {
            log.debug "${request_type} Exception: ${e}"
            if (e.getMessage().contains("Forbidden") || e.getMessage().contains("Unauthorized")) {
                log.debug "Refreshing token..."
                login()
            }
        }
        tries++

    }
    return response
}


def configure() {
    def token = device.getDataValue("token")
    if (password && password != "") {
        device.updateDataValue("encryptedPassword", encrypt(password))
        device.removeSetting("password")
        if (!mac_address || mac_address == "" || !device.getDataValue("device_id") || !device.getDataValue("device_id") == "") {
            state.configured = false
        }
        login()
    } else if (!token || token == "") {
        log.debug "Unable to configure -- invalid login"
        return
    }
    if (isConfigurationValid()) {
        schedulePolling()
        state.configured = true
    }
}

def isNotBlank(obj) {
    return obj && obj != ""
}
        
def isConfigurationValid() {
    def token = device.getDataValue("token")
    def device_id = device.getDataValue("device_id")
    def location_id = device.getDataValue("location_id")
    def pw = device.getDataValue("encryptedPassword")
    def user = device.getDataValue("user_id")

    return isNotBlank(token) && isNotBlank(device_id) &&
        isNotBlank(location_id) && isNotBlank(pw) && isNotBlank(user)
}

def login() {
    def uri = "https://api.meetflo.com/api/v1/users/auth"
    def pw = decrypt(device.getDataValue("encryptedPassword"))
    if (!pw || pw == "") {
        log.debug("Login Failed: No password")
    } else {
        def body = [username:username, password:pw]
        def headers = [:] 
        headers.put("Content-Type", "application/json")

        try {
            httpPostJson([headers: headers, uri: uri, body: body]) { response -> def msg = response?.status
                if (logEnable) log.debug("Login received response code ${response?.status}")
                    if (response?.status == 200) {
                        msg = "Success"
                        device.updateDataValue("token",response.data.token)
                        device.updateDataValue("user_id", response.data.tokenPayload.user.user_id)
                        if (!state.configured) { getUserInfo() }
                    }
                    else {
                        log.debug "Login Failed: (${response.status}) ${response.data}"
                        state.configured = false
                    }
              }
        }
        catch (Exception e) {
            log.debug "Login exception: ${e}"
            log.debug "Login Failed: Please confirm your Flo Credentials"
            state.configured = false
        }
    }
}

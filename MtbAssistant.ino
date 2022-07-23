#include <WiFi.h>
#include <WiFiUdp.h>
#include <time.h>

const char* SSID = "BaerTaHome";            //  your network SSID (name)
const char* WiFiPassword = "77478940435180061509";  // your network password (use for WPA, or use as key for WEP)
//const char* ssid = "MtbAssistant"; //  your network SSID (name)
//const char* pass = "b33c8e7879bd";    // your network password (use for WPA, or use as key for WEP)

//IP address to send UDP data to:
// either use the ip address of the server or
// a network broadcast address
const char* udpAddress = "192.168.178.255";
const int udpPort = 18600;
//Are we currently connected?
boolean connected = false;

int fsrVoltage;  // the analog reading converted to voltage
unsigned long fsrResistance;
unsigned long fsrConductance;
long fsrForce;

WiFiUDP udp;

void setup() {
  //Initialize serial and wait for port to open:
  Serial.begin(115200);
  //Connect to the WiFi network
  connectToWiFi(SSID, WiFiPassword);

  pinMode(A6, INPUT);
  pinMode(A7, INPUT);
  pinMode(A4, INPUT);
  pinMode(A5, INPUT);
  pinMode(A0, INPUT);
  pinMode(A3, INPUT);
}

void loop() {
  if (connected) {
    char  sensorValues[80] = "";
    float vout = 0;
    for (int thisSensor = 0; thisSensor < 6; thisSensor++) {
      int sensorReading = 0;
      float vout;
      switch (thisSensor) {
        case 0:
          sensorReading = analogRead(A6);
          vout = (sensorReading * 3.3) / 4095;
          vout = vout * 1.325;
          break;
        case 1:
          sensorReading = analogRead(A7);
          vout = (sensorReading * 3.3) / 4095;
          vout = vout * 1.2;
          break;
        case 2:
          sensorReading = analogRead(A4);
          vout = (sensorReading * 3.3) / 4095;
          vout = vout * 1.9;
          break;
        case 3:
          sensorReading = analogRead(A5);
          vout = (sensorReading * 3.3) / 4095;
          vout = vout * 1.25;
          break;
        case 4:
          sensorReading = analogRead(A0);
          vout = (sensorReading * 3.3) / 4095;
          vout = vout * 1.5;
        case 5:
          sensorReading = analogRead(A3);
          vout = (sensorReading * 3.3) / 4095;
          vout = vout * 1.5;
          break;
        default:
          break;
      }
      Serial.print(vout);
      if (thisSensor != 5) {
        Serial.print('\t');
      } else {
        Serial.print('\n');
      }

      char cstr[16];
      String forceValue = itoa(sensorReading, cstr, 10);
      String helper = ",";
      strcat(sensorValues, forceValue.c_str());
      if (thisSensor != 5) {
        strcat(sensorValues, helper.c_str());
      }
    }
    // send a reply, to the IP address and port that sent us the packet we received
    udp.beginPacket(udpAddress, udpPort);
    //udp.beginPacket("10.133.132.215", localPort);
    udp.printf(sensorValues);
    udp.endPacket();
  }
  delay(200);
}

void connectToWiFi(const char * ssid, const char * pwd) {
  Serial.println("Connecting to WiFi network: " + String(ssid));
  // delete old config
  WiFi.disconnect(true);
  //register event handler
  WiFi.onEvent(WiFiEvent);
  //Initiate connection
  WiFi.begin(ssid, pwd);
  Serial.println("Waiting for WIFI connection...");
}

//wifi event handler
void WiFiEvent(WiFiEvent_t event) {
  switch (event) {
    case SYSTEM_EVENT_STA_GOT_IP:
      //When connected set
      Serial.print("WiFi connected! IP address: ");
      Serial.println(WiFi.localIP());
      //initializes the UDP state
      //This initializes the transfer buffer
      udp.begin(WiFi.localIP(), udpPort);
      connected = true;
      break;
    case SYSTEM_EVENT_STA_DISCONNECTED:
      Serial.println("WiFi lost connection");
      connected = false;
      break;
    default: break;
  }
}

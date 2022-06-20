#include <SPI.h>
#include <WiFiNINA.h>
#include <WiFiUdp.h>
#include <time.h>

int status = WL_IDLE_STATUS;
///////please enter your sensitive data in the Secret tab/arduino_secrets.h
char ssid[] = "BaerTaHome"; //  your network SSID (name)
char pass[] = "77478940435180061509";    // your network password (use for WPA, or use as key for WEP)
int keyIndex = 0;            // your network key Index number (needed only for WEP)

int A0value = 0;
int A01value = 0;

int fsrVoltage;     // the analog reading converted to voltage
unsigned long fsrResistance;
unsigned long fsrConductance;
long fsrForce;

unsigned int localPort = 18600;      // local port to listen on

char packetBuffer[256]; //buffer to hold incoming packet
char ReplyBuffer[] = "acknowledged";       // a string to send back

WiFiUDP Udp;

void setup() {
  //Initialize serial and wait for port to open:
  Serial.begin(9600);

  while (!Serial) {
    ; // wait for serial port to connect. Needed for native USB port only
  }

  // check for the WiFi module:
  if (WiFi.status() == WL_NO_MODULE) {
    Serial.println("Communication with WiFi module failed!");
    // don't continue
    while (true);
  }

  String fv = WiFi.firmwareVersion();
  if (fv < WIFI_FIRMWARE_LATEST_VERSION) {
    Serial.println("Please upgrade the firmware");
  }

  // attempt to connect to Wifi network:
  while (status != WL_CONNECTED) {
    Serial.print("Attempting to connect to SSID: ");
    Serial.println(ssid);
    // Connect to WPA/WPA2 network. Change this line if using open or WEP network:
    status = WiFi.begin(ssid, pass);
    // wait 10 seconds for connection:
    delay(10000);
  }
  Serial.println("Connected to wifi");
  printWifiStatus();
  Serial.println("\nStarting connection to server...");
  // if you get a connection, report back via serial:
  Udp.begin(localPort);
}

void loop() {
  char sensorValues[50] = "";
  for (int thisSensor = 0; thisSensor < 2; thisSensor++) {
    int sensorReading = analogRead(thisSensor);
    Serial.print(sensorReading);
    if (thisSensor != 1) {
      Serial.print('\t');
    } else {
      Serial.print('\n');
    }
    fsrVoltage = map(sensorReading, 0, 1023, 0, 5000);
    int fsrForce = 0;
    if (fsrVoltage == 0) {
      Serial.println("No pressure");
    } else {
      // The voltage = Vcc * R / (R + FSR) where R = 10K and Vcc = 5V
      // so FSR = ((Vcc - V) * R) / V        yay math!
      fsrResistance = 5000 - fsrVoltage;     // fsrVoltage is in millivolts so 5V = 5000mV
      fsrResistance *= 10000;                // 10K resistor
      fsrResistance /= fsrVoltage;
      fsrConductance = 1000000;           // we measure in micromhos so
      fsrConductance /= fsrResistance;
      // Use the two FSR guide graphs to approximate the force
      if (fsrConductance <= 1000) {
        fsrForce = fsrConductance / 80;
      } else {
        fsrForce = fsrConductance - 1000;
        fsrForce /= 30;
      }
      char cstr[16];
      String forceValue = itoa(fsrForce, cstr, 10);
      String helper = ",";
      strcat(sensorValues, forceValue.c_str());
      if (thisSensor != 1) {
        strcat(sensorValues, helper.c_str());
      }
    }
  }
  Serial.println("Contents:");
  Serial.println(sensorValues);
  // send a reply, to the IP address and port that sent us the packet we received
  Udp.beginPacket("192.168.178.255", localPort);
  Udp.write(sensorValues);
  Udp.endPacket();
  delay(200);
}

void printWifiStatus() {
  // print the SSID of the network you're attached to:
  Serial.print("SSID: ");
  Serial.println(WiFi.SSID());
  // print your board's IP address:
  IPAddress ip = WiFi.localIP();
  Serial.print("IP Address: ");
  Serial.println(ip);
  // print the received signal strength:
  long rssi = WiFi.RSSI();
  Serial.print("signal strength (RSSI):");
  Serial.print(rssi);
  Serial.println(" dBm");
}

import com.virtenio.driver.device.ADT7410;
import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.device.MPL115A2;
import com.virtenio.driver.device.SHT21;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.i2c.I2C;
import com.virtenio.driver.i2c.I2CException;
import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.driver.spi.NativeSPI;

public class Preon32Sensor {

    private ADXL345 accelerationSensor;
    private SHT21 humiditySensor;
    private MPL115A2 pressureSensor;
    private ADT7410 temperatureSensor;
    
    private short[] accelValue = new short[3];
    private float humidityValue;
    private float pressureValue;
    private float temperatureValue;

    public void init() {
        try {
            NativeI2C i2c = NativeI2C.getInstance(1); //Cuman butuh 1 karena I2C & SPIO berbagi (Ga cuman dipakai 1 sensor)
            NativeSPI spi = NativeSPI.getInstance(0);
            GPIO shutDownPin = NativeGPIO.getInstance(12); //GPIO 1 pin = 1 kegunaan. Contoh: Pin 12 = Hanya untuk shutdown
            GPIO chipSelectPin = NativeGPIO.getInstance(20);
            GPIO resetPin = NativeGPIO.getInstance(24);

            i2c.open(I2C.DATA_RATE_400);
            spi.open(ADXL345.SPI_MODE, ADXL345.SPI_BIT_ORDER, ADXL345.SPI_MAX_SPEED);

            temperatureSensor = new ADT7410(i2c, ADT7410.ADDR_0, null, null);
            temperatureSensor.open();

            humiditySensor = new SHT21(i2c);
            humiditySensor.open();
            // humiditySensor.setResolution(SHT21.RESOLUTION_RH12_T14); //Ini ga harus?

            accelerationSensor = new ADXL345(spi, chipSelectPin);
            accelerationSensor.open();
            // accelerationSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_2G);
            // accelerationSensor.setDataRate(ADXL345.DATA_RATE_3200HZ);
            // accelerationSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);

            pressureSensor = new MPL115A2(i2c, resetPin, shutDownPin);
            // pressureSensor = new MPL115A2(i2c, null, null); // Mungkin bisa seperti Temperature?
            pressureSensor.open();
            // pressureSensor.setReset(false);
            // pressureSensor.setShutdown(false);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getTemperatureValue() {
        senseTemperature();
        return ""+(Math.round(temperatureValue * 100.0) / 100.0);
    }

    public String getAccelValue() {
        senseAcceleration();
        return this.accelValue[0]+","+this.accelValue[1]+","+this.accelValue[2];
    }

    public String getPressureValue() {
        sensePressure();
        return ""+(Math.round(pressureValue * 100.0) / 100.0);
    }

    public String getHumidityValue() {
        senseHumidity();
        return  ""+(Math.round(humidityValue));
    }

    private void senseTemperature() {
        try {
            temperatureValue = temperatureSensor.getTemperatureCelsius();
        } catch (I2CException e) {
            e.printStackTrace();
        }
    }

    private void senseAcceleration() {
        try {
            accelerationSensor.getValuesRaw(accelValue, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sensePressure() {
        try {
            pressureSensor.startBothConversion();
            Thread.sleep(MPL115A2.BOTH_CONVERSION_TIME);
            int pressurePr = pressureSensor.getPressureRaw();
            int tempRaw = pressureSensor.getTemperatureRaw();
            pressureValue = pressureSensor.compensate(pressurePr, tempRaw);
        } catch (Exception e) {
            System.out.println("MPL115A2 error");
        }
    }

    private void senseHumidity() {
        try {
            // humidity conversion
            Thread.sleep(1000);
            humiditySensor.startRelativeHumidityConversion();
            Thread.sleep(100);
            int rawRH = humiditySensor.getRelativeHumidityRaw();
            float rh = SHT21.convertRawRHToRHw(rawRH);
            humidityValue = rh;
        } catch (Exception e) {
            System.out.println("SHT21 error");
        }

    }
}

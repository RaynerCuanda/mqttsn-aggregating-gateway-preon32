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

    private ADXL345 vibrationSensor;
    private SHT21 humiditySensor;
    private MPL115A2 pressureSensor;
    private ADT7410 temperatureSensor;
    
    private short[] vibrationValue = new short[3];
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
            humiditySensor.setResolution(SHT21.RESOLUTION_RH12_T14);

            vibrationSensor = new ADXL345(spi, chipSelectPin);
            vibrationSensor.open();
            vibrationSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_2G);
            vibrationSensor.setDataRate(ADXL345.DATA_RATE_3200HZ);
            vibrationSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);

            pressureSensor = new MPL115A2(i2c, resetPin, shutDownPin);
            pressureSensor.open();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getTemperatureValue() {
        senseTemperature();
        return ""+(Math.round(temperatureValue * 100.0) / 100.0);
    }

    public String getVibrationValue() {
        senseVibration();
        return this.vibrationValue[0]+","+this.vibrationValue[1]+","+this.vibrationValue[2];
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

    private void senseVibration() {
        try {
            vibrationSensor.getValuesRaw(vibrationValue, 0);
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
            humiditySensor.startRelativeHumidityConversion();
            Thread.sleep(SHT21.MAX_HUMIDITY_CONVERSION_TIME_R12);
            humidityValue = humiditySensor.getRelativeHumidityAboveWater();
        } catch (Exception e) {
            System.out.println("SHT21 error");
        }

    }
}

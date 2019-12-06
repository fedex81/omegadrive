/*
 * GenesisBackupMemoryMapper
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 21/10/19 13:51
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package omegadrive.cart.mapper.md;

import omegadrive.SystemLoader;
import omegadrive.bus.gen.GenesisBus;
import omegadrive.cart.MdCartInfoProvider;
import omegadrive.cart.loader.MdLoader;
import omegadrive.cart.mapper.BackupMemoryMapper;
import omegadrive.cart.mapper.RomMapper;
import omegadrive.util.Size;
import omegadrive.util.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MdBackupMemoryMapper extends BackupMemoryMapper implements RomMapper {

    private static Logger LOG = LogManager.getLogger(MdBackupMemoryMapper.class.getSimpleName());

    public static boolean SRAM_AVAILABLE;
    public static long SRAM_START_ADDRESS;
    public static long SRAM_END_ADDRESS;

    private static boolean verbose = false;
    private static String fileType = "srm";
    private RomMapper baseMapper;
    private MdCartInfoProvider cartridgeInfoProvider;
    private SramMode sramMode = SramMode.DISABLE;
    private MdLoader.Entry entry = MdLoader.NO_EEPROM;
    private I2cEeprom i2c = I2cEeprom.NO_OP;

    private MdBackupMemoryMapper(String romName, int size) {
        super(SystemLoader.SystemType.GENESIS, fileType, romName, size);
    }

    public static RomMapper createInstance(RomMapper baseMapper, MdCartInfoProvider cart, MdLoader.Entry entry) {
        return createInstance(baseMapper, cart, SramMode.DISABLE, entry);
    }

    private static RomMapper createInstance(RomMapper baseMapper, MdCartInfoProvider cart,
                                            SramMode sramMode, MdLoader.Entry entry) {
        int size = entry.eeprom == null ? MdCartInfoProvider.DEFAULT_SRAM_BYTE_SIZE : entry.eeprom.size;
        MdBackupMemoryMapper mapper = new MdBackupMemoryMapper(cart.getRomName(), size);
        mapper.baseMapper = baseMapper;
        mapper.sramMode = sramMode;
        mapper.cartridgeInfoProvider = cart;
        mapper.entry = entry;
        mapper.i2c = I2cEeprom.createInstance(entry);
        SRAM_START_ADDRESS = mapper.cartridgeInfoProvider.getSramStart();
        SRAM_START_ADDRESS = SRAM_START_ADDRESS > 0 ? SRAM_START_ADDRESS : MdCartInfoProvider.DEFAULT_SRAM_START_ADDRESS;
        SRAM_END_ADDRESS = mapper.cartridgeInfoProvider.getSramEnd();
        SRAM_END_ADDRESS = SRAM_END_ADDRESS > 0 ? SRAM_END_ADDRESS : MdCartInfoProvider.DEFAULT_SRAM_END_ADDRESS;
        SRAM_AVAILABLE = true; //mapper.cartridgeInfoProvider.isSramEnabled();
        LOG.info("BackupMemoryMapper created, using folder: " + mapper.sramFolder);
        mapper.initBackupFileIfNecessary();
        return mapper;
    }

    public static RomMapper getOrCreateInstance(RomMapper baseMapper,
                                                RomMapper currentMapper,
                                                MdCartInfoProvider cartridgeInfoProvider,
                                                SramMode sramMode) {
        if (baseMapper != currentMapper) {
            currentMapper.setSramMode(sramMode);
            return currentMapper;
        }
        return createInstance(baseMapper, cartridgeInfoProvider, sramMode, MdLoader.NO_EEPROM);
    }

    private static boolean noOverlapBetweenRomAndSram() {
        return SRAM_START_ADDRESS > GenesisBus.ROM_END_ADDRESS;
    }

    public static void logInfo(String str, Object... res) {
        if (verbose) {
            LOG.info(str, res);
        }
    }

    @Override
    public void setSramMode(SramMode sramMode) {
        if (this.sramMode != sramMode) {
            LOG.info("SramMode from: " + this.sramMode + " to: " + sramMode);
        }
        this.sramMode = sramMode;
    }

    @Override
    public long readData(long address, Size size) {
        return entry.eeprom == null ? readDataSram(address, size) : readDataEeprom(address, size);
    }


    @Override
    public void writeData(long address, long data, Size size) {
        if (entry.eeprom == null) {
            writeDataSram(address, data, size);
        } else {
            writeDataEeprom(address, data, size);
        }
    }

    private long readDataSram(long address, Size size) {
        address = address & 0xFF_FFFF;
        boolean noOverlap = noOverlapBetweenRomAndSram();
        boolean sramRead = sramMode != SramMode.DISABLE;
        sramRead |= noOverlap; //if no overlap allow to read
        sramRead &= address >= SRAM_START_ADDRESS && address <= SRAM_END_ADDRESS;
        if (sramRead) {
            initBackupFileIfNecessary();
            address = (address & 0xFFFF);
            long res = Util.readSram(sram, size, address);
            logInfo("SRAM read at: {} {}, result: {} ", address, size, res);
            return res;
        }
        return baseMapper.readData(address, size);
    }

    private void writeDataSram(long address, long data, Size size) {
        address = address & 0xFF_FFFF;
        boolean sramWrite = sramMode == SramMode.READ_WRITE;
        sramWrite |= noOverlapBetweenRomAndSram();  //if no overlap allow to write
        sramWrite &= address >= SRAM_START_ADDRESS && address <= SRAM_END_ADDRESS;
        if (sramWrite) {
            initBackupFileIfNecessary();
            address = (address & 0xFFFF);
            logInfo("SRAM write at: {} {}, data: {} ", address, size, data);
            Util.writeSram(sram, size, (int) address, data);
        } else {
            baseMapper.writeData(address, data, size);
        }
    }

    private long readDataEeprom(long address, Size size) {
        address = address & 0xFF_FFFF;
        boolean noOverlap = noOverlapBetweenRomAndSram();
        boolean eepromRead = sramMode != SramMode.DISABLE;
        eepromRead |= noOverlap; //if no overlap allow to read
        eepromRead &= address >= SRAM_START_ADDRESS && address <= SRAM_END_ADDRESS;
        if (eepromRead) {
            initBackupFileIfNecessary();
            long res = i2c.eeprom_i2c_out();
            logInfo("EEPROM read at: {} {}, result: {} ", address, size, res);
            return res;
        }
        return baseMapper.readData(address, size);
    }

    private void writeDataEeprom(long address, long data, Size size) {
        address = address & 0xFF_FFFF;
        boolean eepromWrite = sramMode == SramMode.READ_WRITE;
        eepromWrite |= noOverlapBetweenRomAndSram();  //if no overlap allow to write
        eepromWrite &= address >= SRAM_START_ADDRESS && address <= SRAM_END_ADDRESS;
        if (eepromWrite) {
            initBackupFileIfNecessary();
            i2c.eeprom_i2c_in((int) (data & 0xFF));
            logInfo("EEPROM write at: {} {}, data: {} ", address, size, data);
        } else {
            baseMapper.writeData(address, data, size);
        }
    }

    @Override
    public void closeRom() {
        writeFile();
    }
}
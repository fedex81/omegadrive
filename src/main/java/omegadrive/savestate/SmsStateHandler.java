/*
 * SmsStateHandler
 * Copyright (c) 2018-2019 Federico Berti
 * Last modified: 01/07/19 15:09
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

package omegadrive.savestate;

import omegadrive.bus.z80.SmsBus;
import omegadrive.bus.z80.Z80BusProvider;
import omegadrive.memory.IMemoryProvider;
import omegadrive.vdp.SmsVdp;
import omegadrive.vdp.model.BaseVdpProvider;
import omegadrive.z80.Z80Provider;

public interface SmsStateHandler extends BaseStateHandler {

    SmsStateHandler EMPTY_STATE = new SmsStateHandler() {
        @Override
        public void loadVdp(BaseVdpProvider vdp, IMemoryProvider memory) {

        }

        @Override
        public void loadZ80(Z80Provider z80, Z80BusProvider bus) {

        }

        @Override
        public void saveVdp(BaseVdpProvider vdp, IMemoryProvider memory) {

        }

        @Override
        public void saveZ80(Z80Provider z80, Z80BusProvider bus) {

        }

        @Override
        public void loadMemory(IMemoryProvider mem, SmsVdp vdp) {

        }

        @Override
        public void saveMemory(IMemoryProvider mem, SmsVdp vdp) {

        }

        @Override
        public Type getType() {
            return null;
        }

        @Override
        public String getFileName() {
            return null;
        }

        @Override
        public int[] getData() {
            return new int[0];
        }
    };

    default void processState(SmsVdp vdp, Z80Provider z80, SmsBus bus, IMemoryProvider mem) {
        if (getType() == Type.LOAD) {
            loadZ80(z80, bus);
            loadVdp(vdp, mem);
            loadMemory(mem, vdp);
            vdp.forceFullRedraw();
            bus.reloadBanking();
            LOG.info("Savestate loaded from: {}", getFileName());
        } else {
            saveZ80(z80, bus);
            saveVdp(vdp, mem);
            saveMemory(mem, vdp);
        }
    }

    void loadVdp(BaseVdpProvider vdp, IMemoryProvider memory);

    void loadZ80(Z80Provider z80, Z80BusProvider bus);

    void saveVdp(BaseVdpProvider vdp, IMemoryProvider memory);

    void saveZ80(Z80Provider z80, Z80BusProvider bus);

    void loadMemory(IMemoryProvider mem, SmsVdp vdp);

    void saveMemory(IMemoryProvider mem, SmsVdp vdp);
}
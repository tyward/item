/*
 * Copyright 2014 Tyler Ward.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * This code is part of the reference implementation of http://arxiv.org/abs/1409.6075
 * 
 * This is provided as an example to help in the understanding of the ITEM model system.
 */
package edu.columbia.tjw.gsesf.types;

import edu.columbia.tjw.item.util.HashTool;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

/**
 *
 * @author tyler
 */
public class HashTableBackedEnumFamily
{
    private final String _insertString;
    private final HashTool _hasher;
    private final PreparedStatement _stat;
    private final Set<String> _hashSet;

    public HashTableBackedEnumFamily(final Connection conn_, final String tableName_, final String idName_, final String valueName_) throws SQLException
    {
        _insertString = "INSERT INTO " + tableName_ + " (" + idName_ + ", " + valueName_ + ") VALUES (?, ?) ON CONFLICT DO NOTHING";
        _hasher = new HashTool();
        _hashSet = new HashSet<>();
        _stat = conn_.prepareStatement(_insertString);
    }

    public synchronized long generateMemberId(final String input_) throws SQLException
    {
        final long hash = _hasher.stringToLong(input_);

        upsertValue(hash, input_);

        return hash;
    }

    private synchronized void upsertValue(final long hash_, final String val_) throws SQLException
    {
        if (_hashSet.contains(val_))
        {
            //We have already handled this one, just skip out.
            return;
        }

        //Do the upsert....
        _stat.setLong(1, hash_);
        _stat.setString(2, val_);
        _stat.execute();
    }

}

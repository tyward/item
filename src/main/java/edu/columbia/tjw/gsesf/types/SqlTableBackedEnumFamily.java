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

import edu.columbia.tjw.item.util.EnumFamily;
import edu.columbia.tjw.item.util.EnumMember;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 *
 * @author tyler
 */
public class SqlTableBackedEnumFamily
{
    private final String _selectString;
    private final String _insertString;
    private final Map<String, TableBackedEnumMember> _members;
    private final Set<Integer> _usedIds;

    private EnumFamily<TableBackedEnumMember> _family = null;

    public SqlTableBackedEnumFamily(final Connection conn_, final String tableName_, final String idName_, final String valueName_) throws SQLException
    {
        final String selectAllString = "SELECT " + idName_ + ", " + valueName_ + " FROM " + tableName_;

        _selectString = selectAllString + " WHERE " + valueName_ + " = ?";

        _members = new TreeMap<>();
        _usedIds = new TreeSet<>();

        try (final PreparedStatement stat = conn_.prepareStatement(selectAllString))
        {
            try (final ResultSet res = stat.executeQuery())
            {
                extractMembers(res);
            }
        }

        _insertString = "INSERT INTO " + tableName_ + " (" + valueName_ + ") VALUES (?)";
    }

    public synchronized TableBackedEnumMember getMemberByName(final String name_, final Connection conn_) throws SQLException
    {
        final TableBackedEnumMember member = selectMembers(name_, conn_);

        if (null != member)
        {
            return member;
        }

        //Not in table, need to insert it. 
        try (final PreparedStatement insertStatement = conn_.prepareStatement(_insertString))
        {
            insertStatement.setString(1, name_);
            insertStatement.executeUpdate();
        }
        catch (final SQLException e)
        {
            //Did this fail because someone else inserted the enum member? Try again to make sure.
            final TableBackedEnumMember inserted = selectMembers(name_, conn_);

            if (null != inserted)
            {
                return inserted;
            }

            throw new SQLException(e);
        }

        final TableBackedEnumMember inserted = selectMembers(name_, conn_);

        if (null == inserted)
        {
            throw new NullPointerException("Unable to select or insert enum member.");
        }

        return inserted;
    }

    private TableBackedEnumMember selectMembers(final String name_, final Connection conn_) throws SQLException
    {
        if (_members.containsKey(name_))
        {
            return _members.get(name_);
        }

        try (final PreparedStatement selectStatement = conn_.prepareStatement(_selectString))
        {
            selectStatement.setString(1, name_);

            try (final ResultSet res = selectStatement.executeQuery())
            {
                extractMembers(res);
            }
        }

        return _members.get(name_);
    }

    private void extractMembers(final ResultSet res_) throws SQLException
    {
        while (res_.next())
        {
            final int id = res_.getInt(1);
            final String name = res_.getString(2);

            final TableBackedEnumMember member = new TableBackedEnumMember(_members.size(), id, name);
            this.addToMap(member);
        }
    }

    private synchronized EnumFamily<TableBackedEnumMember> getFamily()
    {
        if (null != _family)
        {
            return _family;
        }

        final TableBackedEnumMember[] values = _members.values().toArray(new TableBackedEnumMember[_members.size()]);

        _family = new EnumFamily<>(values, false);
        return _family;
    }

    private synchronized void addToMap(final TableBackedEnumMember member_)
    {
        if (_members.containsKey(member_.name()))
        {
            throw new IllegalArgumentException("Names cannot be duplicated.");
        }
        if (_usedIds.contains(member_.getId()))
        {
            throw new IllegalArgumentException("IDs cannot be duplicated.");
        }

        _members.put(member_.name(), member_);
        _usedIds.add(member_.getId());
        _family = null;
    }

    public final class TableBackedEnumMember implements EnumMember<TableBackedEnumMember>
    {
        private static final long serialVersionUID = 5484437322002483959L;
        private final int _ordinal;
        private final int _id;
        private final String _name;

        private TableBackedEnumMember(final int ordinal_, final int id_, final String name_)
        {
            _id = id_;
            _name = name_;
            _ordinal = ordinal_;
        }

        public int getId()
        {
            return _id;
        }

        @Override
        public String name()
        {
            return _name;
        }

        @Override
        public int ordinal()
        {
            return _ordinal;
        }

        @Override
        public EnumFamily<TableBackedEnumMember> getFamily()
        {
            return SqlTableBackedEnumFamily.this.getFamily();
        }

        @Override
        public int compareTo(TableBackedEnumMember o)
        {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

}

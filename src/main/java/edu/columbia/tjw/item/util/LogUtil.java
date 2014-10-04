/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.columbia.tjw.item.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 *
 * @author tyler
 */
public final class LogUtil
{

    static
    {
        final Logger rootLogger = Logger.getLogger("");

        for (final Handler next : rootLogger.getHandlers())
        {
            rootLogger.removeHandler(next);
        }

        rootLogger.addHandler(new InnerHandler());
    }

    private LogUtil()
    {
    }

    public static Logger getLogger(final Class<?> clazz_)
    {
        final String name = clazz_.getName();
        final Logger output = Logger.getLogger(name);
        return output;
    }

    private static final class InnerHandler extends Handler
    {
        private final DateFormat _format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public synchronized void publish(LogRecord record)
        {
            //Let's format this thing nicely. 
            final Date recDate = new Date(record.getMillis());
            final String dateString = _format.format(recDate);
            final String message = record.getMessage();
            final String logger = record.getLoggerName();

            final StringBuffer builder = new StringBuffer();

            builder.append("[");
            builder.append(dateString);
            builder.append("][");
            builder.append(logger);
            builder.append("]: ");
            builder.append(message);

            final String completed = builder.toString();
            System.out.println(completed);

            final Throwable t = record.getThrown();

            if (null != t)
            {
                t.printStackTrace(System.out);
            }
        }

        @Override
        public void flush()
        {
            System.out.flush();
        }

        @Override
        public void close() throws SecurityException
        {
            //Do nothing.
        }

    }

}

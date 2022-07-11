-- phpMyAdmin SQL Dump
-- version 5.2.0
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Erstellungszeit: 11. Jul 2022 um 18:41
-- Server-Version: 10.4.24-MariaDB
-- PHP-Version: 8.1.6

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Datenbank: `e621sync`
--
CREATE DATABASE IF NOT EXISTS `e621sync` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_520_ci;
USE `e621sync`;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `convert_queue`
--

CREATE TABLE `convert_queue` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `download_queue`
--

CREATE TABLE `download_queue` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `poolmap`
--

CREATE TABLE `poolmap` (
  `id` int(11) NOT NULL,
  `pool_id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `pools`
--

CREATE TABLE `pools` (
  `id` int(11) NOT NULL,
  `name` text COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `updated_at` bigint(20) NOT NULL,
  `description` text COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `post_ids` text COLLATE utf8mb4_unicode_520_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci ROW_FORMAT=COMPRESSED;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `posts`
--

CREATE TABLE `posts` (
  `id` int(11) NOT NULL,
  `md5` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `source` text COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `score` smallint(6) NOT NULL,
  `image_width` smallint(6) NOT NULL,
  `image_height` smallint(6) NOT NULL,
  `tag_string` text COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `file_ext` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `parent_id` int(11) NOT NULL,
  `description` mediumtext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `is_deleted` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `downloaded` tinyint(1) NOT NULL DEFAULT 0,
  `rename_ext` tinyint(128) NOT NULL DEFAULT 0,
  `thumbnail` tinyint(1) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci ROW_FORMAT=COMPRESSED
PARTITION BY RANGE COLUMNS(`rename_ext`)
(
PARTITION p0 VALUES LESS THAN (1) ENGINE=InnoDB,
PARTITION p1 VALUES LESS THAN (MAXVALUE) ENGINE=InnoDB
);

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `rename_ext`
--

CREATE TABLE `rename_ext` (
  `id` int(11) NOT NULL,
  `rename_ext` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `system`
--

CREATE TABLE `system` (
  `k` varchar(255) COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `v` varchar(255) COLLATE utf8mb4_unicode_520_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci ROW_FORMAT=COMPRESSED;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `tagmap`
--

CREATE TABLE `tagmap` (
  `id` int(11) NOT NULL,
  `post_id` int(11) NOT NULL,
  `tag_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci ROW_FORMAT=DYNAMIC;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `tags`
--

CREATE TABLE `tags` (
  `id` int(11) NOT NULL,
  `name` text COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `category` tinyint(4) NOT NULL,
  `post_count` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci ROW_FORMAT=COMPRESSED;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `tag_aliases`
--

CREATE TABLE `tag_aliases` (
  `id` int(11) NOT NULL,
  `antecedent_name` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `consequent_name` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `status` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci;

-- --------------------------------------------------------

--
-- Tabellenstruktur für Tabelle `tag_implications`
--

CREATE TABLE `tag_implications` (
  `id` int(11) NOT NULL,
  `antecedent_name` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `consequent_name` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL,
  `status` tinytext COLLATE utf8mb4_unicode_520_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_520_ci ROW_FORMAT=COMPRESSED;

--
-- Indizes der exportierten Tabellen
--

--
-- Indizes für die Tabelle `convert_queue`
--
ALTER TABLE `convert_queue`
  ADD PRIMARY KEY (`id`),
  ADD KEY `post_id` (`post_id`);

--
-- Indizes für die Tabelle `download_queue`
--
ALTER TABLE `download_queue`
  ADD PRIMARY KEY (`id`);

--
-- Indizes für die Tabelle `poolmap`
--
ALTER TABLE `poolmap`
  ADD PRIMARY KEY (`id`),
  ADD KEY `pool_id` (`pool_id`),
  ADD KEY `post_id` (`post_id`);

--
-- Indizes für die Tabelle `pools`
--
ALTER TABLE `pools`
  ADD PRIMARY KEY (`id`),
  ADD KEY `updated_at` (`updated_at`);

--
-- Indizes für die Tabelle `posts`
--
ALTER TABLE `posts`
  ADD PRIMARY KEY (`id`,`rename_ext`) USING BTREE,
  ADD KEY `downloaded` (`downloaded`),
  ADD KEY `is_deleted` (`is_deleted`(255)),
  ADD KEY `score` (`score`),
  ADD KEY `file_ext` (`file_ext`(255)),
  ADD KEY `idx_rename_id` (`id`),
  ADD KEY `rename_ext` (`rename_ext`);

--
-- Indizes für die Tabelle `rename_ext`
--
ALTER TABLE `rename_ext`
  ADD PRIMARY KEY (`id`),
  ADD KEY `rename_ext` (`rename_ext`(255));

--
-- Indizes für die Tabelle `system`
--
ALTER TABLE `system`
  ADD PRIMARY KEY (`k`);

--
-- Indizes für die Tabelle `tagmap`
--
ALTER TABLE `tagmap`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_post_id` (`post_id`),
  ADD KEY `idx_tag_id` (`tag_id`);

--
-- Indizes für die Tabelle `tags`
--
ALTER TABLE `tags`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_name` (`name`(768)),
  ADD KEY `category` (`category`);

--
-- Indizes für die Tabelle `tag_aliases`
--
ALTER TABLE `tag_aliases`
  ADD PRIMARY KEY (`id`),
  ADD KEY `antecedent_name` (`antecedent_name`(255)),
  ADD KEY `consequent_name` (`consequent_name`(255));

--
-- Indizes für die Tabelle `tag_implications`
--
ALTER TABLE `tag_implications`
  ADD PRIMARY KEY (`id`),
  ADD KEY `antecedent_name` (`antecedent_name`(255)),
  ADD KEY `consequent_name` (`consequent_name`(255));

--
-- AUTO_INCREMENT für exportierte Tabellen
--

--
-- AUTO_INCREMENT für Tabelle `convert_queue`
--
ALTER TABLE `convert_queue`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `download_queue`
--
ALTER TABLE `download_queue`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `poolmap`
--
ALTER TABLE `poolmap`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `rename_ext`
--
ALTER TABLE `rename_ext`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT für Tabelle `tagmap`
--
ALTER TABLE `tagmap`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;

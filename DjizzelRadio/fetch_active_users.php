<?php
$root = dirname(__DIR__, 3);
require_once($root . '/system/config.php');

header('Content-Type: application/json; charset=UTF-8');
header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
header('Pragma: no-cache');

function djizzelJson($payload)
{
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function djizzelAvatar($avatar)
{
    $avatar = trim((string)$avatar);
    if ($avatar === '') {
        return 'default_images/avatar/default_male.png';
    }

    if (function_exists('myAvatar')) {
        return myAvatar($avatar);
    }

    if (preg_match('~^(?:https?:)?//~i', $avatar) || strpos($avatar, 'data:') === 0) {
        return $avatar;
    }

    return $avatar;
}

if (!isset($mysqli) || !($mysqli instanceof mysqli)) {
    djizzelJson(array('users' => array(), 'online_count' => 0));
}

$now = time();
$onlineCutoff = $now - 300;
$users = array();

$queryWithBot = "
    SELECT user_id, user_name, user_tumb, user_rank, last_action
    FROM boom_users
    WHERE user_rank < 999
      AND (user_bot = 0 OR user_bot IS NULL)
    ORDER BY last_action DESC
    LIMIT 18
";

$queryFallback = "
    SELECT user_id, user_name, user_tumb, user_rank, last_action
    FROM boom_users
    WHERE user_rank < 999
    ORDER BY last_action DESC
    LIMIT 18
";

$result = $mysqli->query($queryWithBot);
if (!$result) {
    $result = $mysqli->query($queryFallback);
}

if ($result) {
    while ($row = $result->fetch_assoc()) {
        $name = trim((string)($row['user_name'] ?? ''));
        if ($name === '') {
            continue;
        }

        $lastAction = (int)($row['last_action'] ?? 0);
        $users[] = array(
            'id' => (int)($row['user_id'] ?? 0),
            'name' => $name,
            'avatar' => djizzelAvatar($row['user_tumb'] ?? ''),
            'online' => $lastAction >= $onlineCutoff,
            'last_action' => $lastAction,
        );
    }
    $result->free();
}

$countSqlWithBot = "
    SELECT COUNT(*) AS total
    FROM boom_users
    WHERE user_rank < 999
      AND last_action >= {$onlineCutoff}
      AND (user_bot = 0 OR user_bot IS NULL)
";
$countSqlFallback = "
    SELECT COUNT(*) AS total
    FROM boom_users
    WHERE user_rank < 999
      AND last_action >= {$onlineCutoff}
";

$countResult = $mysqli->query($countSqlWithBot);
if (!$countResult) {
    $countResult = $mysqli->query($countSqlFallback);
}

$onlineCount = 0;
if ($countResult) {
    $countRow = $countResult->fetch_assoc();
    $onlineCount = (int)($countRow['total'] ?? 0);
    $countResult->free();
}

djizzelJson(array(
    'users' => $users,
    'online_count' => $onlineCount,
    'generated_at' => $now,
));
